package com.adrian.volumes.book;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OpenLibraryClientTest {
    private HttpServer server;
    private OpenLibraryClient client;
    private int status = 200;
    private String body = "{\"key\":\"/books/OL123M\",\"covers\":[-1,42]}";
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/isbn/9780140449242.json", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        client = new OpenLibraryClient("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
    }

    @AfterEach
    void stop() { client.close(); server.stop(0); }

    @Test
    void resolvesEditionAndPositiveCoverIdUsingCanonicalIsbn() {
        var edition = client.findEdition("0-14-044924-8");
        assertEquals(new BookEdition("9780140449242", "OL123M", 42L), edition);
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg?default=false", edition.coverUrl());
        assertEquals("https://openlibrary.org/books/OL123M", edition.sourceUrl());
        assertEquals(1, calls.get());
    }

    @Test
    void editionWithoutACoverIsStillValid() {
        body = "{\"key\":\"/books/OL123M\",\"covers\":[-1]}";
        assertNull(client.findEdition("9780140449242").coverUrl());
    }

    @Test
    void rejectsInvalidIsbnBeforeNetworkAccess() {
        assertThrows(IllegalArgumentException.class, () -> client.findEdition("9780140449243"));
        assertEquals(0, calls.get());
    }

    @Test
    void distinguishesAnUnknownEdition() {
        status = 404;
        assertStatus(404);
    }

    @Test
    void mapsProviderFailureToTemporaryUnavailability() {
        status = 429;
        assertStatus(503);
    }

    @Test
    void rejectsMalformedJson() {
        body = "{";
        assertStatus(502);
    }

    @Test
    void rejectsAWorkInsteadOfAnEdition() {
        body = "{\"key\":\"/works/OL123W\"}";
        assertStatus(502);
    }

    @Test
    void boundsRequestRate() {
        client.findEdition("9780140449242");
        assertStatus(429);
        assertEquals(1, calls.get());
    }

    private void assertStatus(int expected) {
        var error = assertThrows(ResponseStatusException.class, () -> client.findEdition("9780140449242"));
        assertEquals(expected, error.getStatusCode().value());
    }
}
