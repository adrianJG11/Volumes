package com.adrian.volumes.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
        reserveRequest();
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/isbn/" + isbn + ".json"))
            .timeout(timeout).header("Accept", "application/json")
            .header("User-Agent", "Volumes/0.1 (https://github.com/adrianJG11/Volumes)").GET().build();
        try {
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

    // Bound external lookups to one per second per application instance. Catalog reads use PostgreSQL only.
    private synchronized void reserveRequest() {
        long now = System.nanoTime();
        if (now < nextRequest)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a second before another ISBN lookup.");
        nextRequest = now + Duration.ofSeconds(1).toNanos();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Open Library is unavailable. Try again later.");
    }

    private static ResponseStatusException malformed() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Open Library returned an invalid edition.");
    }
}
