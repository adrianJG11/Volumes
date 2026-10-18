package com.adrian.volumes.book;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fallback cover source when Open Library has none: Apple's free iTunes Search API, no key required. */
@Component
public class ItunesClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;

    public ItunesClient(@Value("${itunes.base-url:https://itunes.apple.com}") String baseUrl) { this.baseUrl = baseUrl; }

    @jakarta.annotation.PreDestroy
    public void close() { http.close(); }

    /** Artwork for the exact ISBN, enlarged from 100x100 to 600x600; null when iTunes has none or fails. */
    public String findCover(String isbn) {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/lookup?isbn=" + isbn)).timeout(Duration.ofSeconds(5)).GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            String url = json.readTree(response.body()).path("results").path(0).path("artworkUrl100").asString("")
                .replaceFirst("/100x100bb\\.(jpg|png)$", "/600x600bb.$1");
            return CoverUrls.allowed(url) ? url : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.io.IOException | tools.jackson.core.JacksonException exception) {
            return null;
        }
    }
}
