package com.adrian.volumes.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class OpenLibraryClient {
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final Duration timeout;
    private long nextRequest;

    public OpenLibraryClient(@Value("${open-library.base-url:https://openlibrary.org}") String baseUrl,
                             @Value("${open-library.timeout:5s}") Duration timeout) {
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    @jakarta.annotation.PreDestroy
    public void close() { http.close(); }

    public BookEdition findEdition(String input) {
        String isbn = Isbn.normalize(input);
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/isbn/" + isbn + ".json"))
            .timeout(timeout).header("Accept", "application/json")
            .header("User-Agent", "Volumes/0.1 (https://github.com/adrianJG11/Volumes)").GET().build();
        try {
            reserveRequest(false);
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Open Library edition found for this ISBN.");
            if (response.statusCode() != 200)
                throw unavailable();
            var data = json.readTree(response.body());
            String key = data.path("key").asString("");
            if (!key.matches("/books/OL[0-9]+M")) throw malformed();
            Long coverId = null;
            var covers = data.path("covers");
            if (covers.isArray()) {
                for (var cover : covers) {
                    if (cover.isIntegralNumber() && cover.canConvertToLong() && cover.asLong() > 0) {
                        coverId = cover.asLong();
                        break;
                    }
                }
            }
            return new BookEdition(isbn, key.substring("/books/".length()), coverId == null ? null : CoverUrls.openLibraryCover(coverId));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        } catch (tools.jackson.core.JacksonException exception) {
            throw malformed();
        }
    }

    public List<BookSuggestion> search(String input) {
        String query = input == null ? "" : input.strip();
        if (query.length() < 2 || query.length() > 100)
            throw new IllegalArgumentException("Search with 2 to 100 characters.");
        // lang=en makes the nested edition the best English one, so its title and ISBN match each other.
        // Open Library only returns nested editions when the work key is requested too.
        // Ask for more than we show: relevance alone often ranks adaptations or other languages first.
        String url = baseUrl + "/search.json?lang=en&limit=15&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&fields=key,title,author_name,subject,number_of_pages_median,first_publish_year,cover_i,readinglog_count,"
            + "editions,editions.title,editions.isbn,editions.cover_i";
        try {
            reserveRequest(false);
            var response = http.send(get(url), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            var docs = json.readTree(response.body()).path("docs");
            if (!docs.isArray()) throw malformedSearch();
            var wanted = words(query);
            JsonNode mostRead = null;
            long mostReaders = -1;
            var others = new ArrayList<Ranked>();
            boolean first = true;
            for (var work : docs) {
                boolean topHit = first;
                first = false;
                String workTitle = text(work.path("title"));
                if (workTitle != null && workTitle.toLowerCase(Locale.ROOT).contains("[adaptation]")) continue;
                var edition = work.path("editions").path("docs").path(0);
                String title = text(edition.path("title"));
                if (title == null) title = workTitle;
                if (title == null) continue;
                var found = words(title);
                boolean matches = found.containsAll(wanted) || (workTitle != null && words(workTitle).containsAll(wanted));
                // Open Library's top hit counts even with a different title ("1984" finds "Nineteen Eighty-Four");
                // other works must contain every typed word, so "dune messiah" never jumps to "Dune".
                if (!matches && !topHit) continue;
                long readers = positiveOr(work.path("readinglog_count"));
                if (readers > mostReaders) {
                    mostRead = work;
                    mostReaders = readers;
                }
                // The nested edition is only shown when its own title matches: never a Chinese-titled edition.
                if (found.containsAll(wanted))
                    others.add(new Ranked(fromNestedEdition(work, edition, title), found.equals(wanted), readers, text(work.path("key"))));
            }
            var results = new ArrayList<BookSuggestion>(mostRead == null ? List.of() : wellKnownEditions(mostRead, wanted));
            String mostReadKey = mostRead == null ? null : text(mostRead.path("key"));
            var isbns = new java.util.HashSet<String>();
            results.forEach(item -> isbns.add(item.isbn()));
            // Exact titles first, then those with an ISBN (it brings the cover), then the most-read work.
            others.stream()
                .filter(item -> results.isEmpty() || !java.util.Objects.equals(item.workKey(), mostReadKey))
                .sorted(java.util.Comparator.comparing(Ranked::exactTitle).reversed()
                    .thenComparing(java.util.Comparator.comparing((Ranked item) -> item.suggestion().isbn() != null).reversed())
                    .thenComparing(java.util.Comparator.comparingLong(Ranked::readers).reversed()))
                .map(Ranked::suggestion)
                .filter(item -> item.isbn() == null || isbns.add(item.isbn()))
                .forEach(results::add);
            return results.stream().limit(5).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        } catch (tools.jackson.core.JacksonException exception) {
            throw malformedSearch();
        }
    }

    private record Ranked(BookSuggestion suggestion, boolean exactTitle, long readers, String workKey) {}

    private BookSuggestion fromNestedEdition(JsonNode work, JsonNode edition, String title) {
        Long cover = positive(edition.path("cover_i"));
        if (cover == null) cover = positive(work.path("cover_i"));
        return new BookSuggestion(title, text(work.path("author_name").path(0)), genre(work.path("subject")),
            pages(positive(work.path("number_of_pages_median"))), isbn(edition.path("isbn")),
            cover == null ? null : CoverUrls.openLibraryCover(cover), year(work));
    }

    // Lower index wins. Open Library has no edition popularity, so publishers stand in for "the edition people read".
    private static final List<String> WELL_KNOWN_PUBLISHERS = List.of(
        "penguin", "oxford", "modern library", "everyman", "vintage", "barnes & noble", "bantam", "wordsworth",
        "signet", "random house", "knopf", "harper", "simon & schuster", "macmillan", "norton", "bloomsbury",
        "scholastic", "houghton mifflin", "del rey", "picador", "faber");

    private record Edition(BookSuggestion suggestion, int publisher, boolean cover, int pages) {}

    // Edition lists of popular works take seconds to download; keep the last 200 works in memory.
    private final java.util.Map<String, List<Edition>> editionCache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, List<Edition>> eldest) { return size() > 200; }
        });

    /**
     * Up to three English editions of the most-read work, one per well-known publisher: for Monte Cristo,
     * Penguin, Oxford and Modern Library instead of print-on-demand reprints. Each publisher's longest
     * edition with a cover wins, so abridged versions lose. Failures fall back to the search results alone.
     */
    private List<BookSuggestion> wellKnownEditions(JsonNode work, java.util.Set<String> wanted) {
        String key = text(work.path("key"));
        if (key == null || !key.matches("/works/OL[0-9]+W")) return List.of();
        var candidates = new ArrayList<>(editionCache.computeIfAbsent(key, ignored -> englishEditions(work, key)));
        if (candidates.isEmpty()) editionCache.remove(key); // retry failed or empty lookups next time
        // Prefer editions titled like the search ("1984" rather than "Nineteen Eighty-Four") when there are any.
        if (candidates.stream().anyMatch(item -> words(item.suggestion().title()).containsAll(wanted)))
            candidates.removeIf(item -> !words(item.suggestion().title()).containsAll(wanted));
        var best = new java.util.TreeMap<Integer, Edition>();
        var better = java.util.Comparator.comparing(Edition::cover).thenComparingInt(Edition::pages);
        for (var item : candidates) best.merge(item.publisher(), item, (a, b) -> better.compare(a, b) >= 0 ? a : b);
        var wellKnown = best.headMap(WELL_KNOWN_PUBLISHERS.size()).values().stream().limit(3).map(Edition::suggestion).toList();
        if (!wellKnown.isEmpty()) return wellKnown;
        var other = best.get(WELL_KNOWN_PUBLISHERS.size());
        return other == null ? List.of() : List.of(other.suggestion());
    }

    private List<Edition> englishEditions(JsonNode work, String key) {
        JsonNode entries;
        try {
            // The first 500 editions hold the usual publishers even for works with hundreds of reprints;
            // the list is large, so it gets twice the normal timeout.
            var request = HttpRequest.newBuilder(URI.create(baseUrl + key + "/editions.json?limit=500"))
                .timeout(timeout.multipliedBy(2)).header("Accept", "application/json")
                .header("User-Agent", "Volumes/0.1 (https://github.com/adrianJG11/Volumes)").GET().build();
            reserveRequest(true);
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.of();
            entries = json.readTree(response.body()).path("entries");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            return List.of();
        }
        if (!entries.isArray()) return List.of();
        var candidates = new ArrayList<Edition>();
        for (var entry : entries) {
            boolean english = false;
            for (var language : entry.path("languages")) english |= "/languages/eng".equals(language.path("key").asString(""));
            String title = text(entry.path("title"));
            String isbn = isbn(entry.path("isbn_13"));
            if (isbn == null) isbn = isbn(entry.path("isbn_10"));
            if (!english || title == null || isbn == null) continue;
            String publisher = text(entry.path("publishers").path(0));
            int rank = WELL_KNOWN_PUBLISHERS.size();
            for (int i = 0; publisher != null && i < WELL_KNOWN_PUBLISHERS.size(); i++)
                if (publisher.toLowerCase(Locale.ROOT).contains(WELL_KNOWN_PUBLISHERS.get(i))) { rank = i; break; }
            Long cover = positive(entry.path("covers").path(0));
            Long pages = positive(entry.path("number_of_pages"));
            if (pages == null) pages = positive(work.path("number_of_pages_median"));
            candidates.add(new Edition(new BookSuggestion(title, text(work.path("author_name").path(0)), genre(work.path("subject")),
                pages(pages), isbn, cover == null ? null : CoverUrls.openLibraryCover(cover), year(work)),
                rank, cover != null, pages == null ? 0 : pages.intValue()));
        }
        return List.copyOf(candidates);
    }

    private HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Accept", "application/json")
            .header("User-Agent", "Volumes/0.1 (https://github.com/adrianJG11/Volumes)").GET().build();
    }

    private static Integer pages(Long pages) { return pages == null || pages > 100_000 ? null : pages.intValue(); }

    private static Integer year(JsonNode work) {
        Long year = positive(work.path("first_publish_year"));
        return year == null || year > 9999 ? null : year.intValue();
    }

    private static long positiveOr(JsonNode node) {
        Long value = positive(node);
        return value == null ? 0 : value;
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of("the", "a", "an", "of", "and", "&");

    /** Lowercase words without accents or articles: "The Count of Monte-Cristo" -> [count, monte, cristo]. */
    static java.util.Set<String> words(String text) {
        String plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        var words = new java.util.HashSet<String>();
        for (String word : plain.split("[^\\p{L}\\p{N}]+"))
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) words.add(word);
        return words;
    }

    // Subjects are free-form ("Fathers and sons, fiction") and roughly ordered by relevance:
    // the first one naming a known genre wins. "History and criticism" describes books about the work.
    private static final String[][] GENRES = {
        {"science fiction", "Science fiction"}, {"fantasy", "Fantasy"}, {"horror", "Horror"}, {"mystery", "Mystery"},
        {"thriller", "Thriller"}, {"romance", "Romance"}, {"poetry", "Poetry"}, {"classic", "Classics"},
        // Before the non-fiction genres, so "France -- History -- Fiction" (a historical novel) counts as fiction.
        {"fiction", "Fiction"}, {"biography", "Biography"}, {"philosophy", "Philosophy"}, {"history", "History"}};

    static String genre(JsonNode subjects) {
        for (var subject : subjects) {
            String value = subject.asString("").toLowerCase(Locale.ROOT);
            if (value.contains("criticism")) continue;
            for (String[] genre : GENRES) if (value.contains(genre[0])) return genre[1];
        }
        return null;
    }

    private static String isbn(JsonNode isbns) {
        if (!isbns.isArray()) return null;
        for (var value : isbns) {
            try {
                if (value.isString()) return Isbn.normalize(value.asString());
            } catch (IllegalArgumentException invalid) {
                // Open Library data contains some invalid ISBNs; try the next one.
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (!node.isString()) return null;
        String value = node.asString().strip();
        return value.isEmpty() || value.length() > 300 ? null : value;
    }

    private static Long positive(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.asLong() > 0 ? node.asLong() : null;
    }

    // ponytail: one limiter per JVM; use a shared limiter only when running multiple instances.
    private synchronized void reserveRequest(boolean wait) throws InterruptedException {
        long delay = nextRequest - System.nanoTime();
        if (delay > 0) {
            if (!wait) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Please wait a second before another Open Library lookup.");
            java.util.concurrent.TimeUnit.NANOSECONDS.sleep(delay);
        }
        nextRequest = System.nanoTime() + Duration.ofSeconds(1).toNanos();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Open Library is unavailable. Try again later.");
    }

    private static ResponseStatusException malformedSearch() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Open Library returned invalid search results.");
    }

    private static ResponseStatusException malformed() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Open Library returned an invalid edition.");
    }
}
