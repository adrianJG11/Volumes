package com.adrian.volumes.book.api;

import com.adrian.volumes.support.ApiClient;
import com.adrian.volumes.user.UserRegistration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Search and cover selection through the real API and database; one local server plays Open Library and iTunes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookSearchFlowTest {
    private static final HttpServer PROVIDERS;
    private static final Map<String, Response> RESPONSES = new ConcurrentHashMap<>();
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<Long> REQUEST_TIMES = new CopyOnWriteArrayList<>();
    private record Response(int status, String body) {}

    static {
        try {
            PROVIDERS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        PROVIDERS.createContext("/", exchange -> {
            var uri = exchange.getRequestURI();
            REQUEST_TIMES.add(System.nanoTime());
            REQUESTS.add(uri.getPath() + "?" + uri.getQuery());
            var response = RESPONSES.getOrDefault(uri.getPath(), new Response(404, "{}"));
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        PROVIDERS.start();
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + PROVIDERS.getAddress().getPort();
        registry.add("open-library.base-url", () -> url);
        registry.add("itunes.base-url", () -> url);
    }

    @LocalServerPort int port;
    @Autowired UserRegistration registration;
    private final JsonMapper json = JsonMapper.builder().build();
    private ApiClient client;

    @BeforeEach
    void signIn() throws Exception {
        RESPONSES.clear();
        REQUEST_TIMES.clear();
        REQUESTS.clear();
        registration.register("reader@example.com", "a search flow passphrase");
        client = new ApiClient(port);
        assertEquals(204, client.login("reader@example.com", "a search flow passphrase").statusCode());
    }

    @AfterEach
    void close() { client.close(); }

    private JsonNode get(String path) throws Exception {
        var response = client.request("GET", path, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    @Test
    void searchShowsWellKnownEditionsOfTheMostReadWork() throws Exception {
        // Open Library pairs the famous work with a Chinese edition; a little-read reprint matches the title.
        RESPONSES.put("/search.json", new Response(200, """
            {"docs":[
              {"key":"/works/OL1W","title":"Le Comte de Monte-Cristo","author_name":["Alexandre Dumas"],"readinglog_count":1653,
               "number_of_pages_median":593,"subject":["Classics","Fiction","Fathers and sons, fiction"],
               "editions":{"docs":[{"title":"基度山伯爵","isbn":["9787560009032"]}]}},
              {"key":"/works/OL2W","title":"The Count of Monte Cristo [adaptation]","readinglog_count":28},
              {"key":"/works/OL3W","title":"The Count of Monte Cristo","author_name":["Reprint Press"],"readinglog_count":3,
               "editions":{"docs":[{"title":"The Count of Monte Cristo","isbn":["9781986964333"]}]}},
              {"key":"/works/OL4W","title":"Monte Cristo Sandwiches","readinglog_count":9000}
            ]}"""));
        RESPONSES.put("/works/OL1W/editions.json", new Response(200, """
            {"entries":[
              {"title":"The Count of Monte Cristo","publishers":["CreateSpace Independent Publishing Platform"],
               "isbn_13":["9781500000004"],"number_of_pages":1400,"covers":[1],"languages":[{"key":"/languages/eng"}]},
              {"title":"The Count of Monte Cristo","publishers":["Penguin Books"],"isbn_13":["9780142000731"],
               "number_of_pages":528,"covers":[2],"languages":[{"key":"/languages/eng"}]},
              {"title":"The Count of Monte Cristo","publishers":["Oxford University Press, USA"],"isbn_13":["9780192827159"],
               "number_of_pages":1161,"covers":[3],"languages":[{"key":"/languages/eng"}]},
              {"title":"The Count of Monte Cristo","publishers":["Penguin Books"],"isbn_13":["9780140449266"],
               "number_of_pages":1312,"covers":[4],"languages":[{"key":"/languages/eng"}]},
              {"title":"El conde de Montecristo","publishers":["Penguin Random House"],"isbn_13":["9788439710349"],
               "number_of_pages":1154,"covers":[5],"languages":[{"key":"/languages/spa"}]}
            ]}"""));
        try (var anonymous = new ApiClient(port)) {
            assertEquals(401, anonymous.request("GET", "/books/search?q=dune", null).statusCode());
        }
        assertEquals(400, client.request("GET", "/books/search?q=x", null).statusCode());
        assertTrue(REQUESTS.isEmpty(), "invalid or anonymous searches must not reach a provider");

        var results = get("/books/search?q=the%20count%20of%20monte%20cristo");
        assertEquals(2, REQUEST_TIMES.size());
        assertTrue(REQUEST_TIMES.get(1) - REQUEST_TIMES.get(0) >= 900_000_000L,
            "the editions follow-up must share the one-second provider limit (100ms scheduling tolerance)");
        var isbns = new java.util.ArrayList<String>();
        results.forEach(item -> isbns.add(item.path("isbn").asString()));
        // Unabridged Penguin first, then Oxford; reprints and other languages after or not at all.
        assertEquals(List.of("9780140449266", "9780192827159", "9781986964333"), isbns);
        var penguin = results.get(0);
        assertEquals("Alexandre Dumas", penguin.path("author").asString());
        assertEquals("Classics", penguin.path("genre").asString());
        assertEquals(1312, penguin.path("pages").asInt());
        assertEquals("https://covers.openlibrary.org/b/id/4-M.jpg?default=false", penguin.path("coverUrl").asString());
        assertEquals("[]", client.request("GET", "/books", null).body(), "searching never changes the catalog");

        // The editions list is remembered: searching again asks Open Library only for the search itself.
        Thread.sleep(1100);
        REQUESTS.clear();
        assertEquals(results, get("/books/search?q=the%20count%20of%20monte%20cristo"));
        assertEquals(1, REQUESTS.size(), REQUESTS.toString());
    }

    @Test
    void savingAnIsbnUsesOpenLibrarysCoverOrElseItunes() throws Exception {
        var created = client.request("POST", "/books",
            "{\"title\":\"The Count of Monte Cristo\",\"author\":\"Alexandre Dumas\",\"genre\":\"Classics\",\"pages\":1154}");
        assertEquals(201, created.statusCode(), created.body());
        String path = "/books/" + json.readTree(created.body()).path("id").asString() + "/edition";

        // Open Library does not know this Spanish edition; iTunes has its artwork.
        RESPONSES.put("/lookup", new Response(200, "{\"results\":[{\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/image/thumb/a/100x100bb.jpg\"}]}"));
        var saved = client.request("PUT", path, "{\"isbn\":\"978-84-397-1034-9\"}");
        assertEquals(200, saved.statusCode(), saved.body());
        var edition = json.readTree(saved.body()).path("edition");
        assertEquals("9788439710349", edition.path("isbn").asString());
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/a/600x600bb.jpg", edition.path("coverUrl").asString());
        assertTrue(edition.path("sourceUrl").isNull());

        // An edition Open Library knows: its cover and link win, and iTunes is not asked.
        Thread.sleep(1100); // Open Library lookups are limited to one per second.
        REQUESTS.clear();
        RESPONSES.put("/isbn/9780140449266.json", new Response(200, "{\"key\":\"/books/OL7M\",\"covers\":[42]}"));
        saved = client.request("PUT", path, "{\"isbn\":\"9780140449266\"}");
        assertEquals(200, saved.statusCode(), saved.body());
        edition = json.readTree(saved.body()).path("edition");
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg?default=false", edition.path("coverUrl").asString());
        assertEquals("https://openlibrary.org/books/OL7M", edition.path("sourceUrl").asString());
        assertTrue(REQUESTS.stream().noneMatch(request -> request.startsWith("/lookup")), REQUESTS.toString());
        assertEquals(edition, get("/books").get(0).path("edition"));
    }
}
