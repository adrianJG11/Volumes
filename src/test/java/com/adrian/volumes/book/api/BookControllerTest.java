package com.adrian.volumes.book.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookControllerTest {
    @LocalServerPort
    private int port;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String VALID = """
        {"title":"  Crime and Punishment  ","author":"Fyodor Dostoevsky","genre":"Fiction","pages":350}
        """;

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/json, application/problem+json");
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void getBooksReturnsAnEmptyJsonArray() throws Exception {
        var response = request("GET", "/books", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertEquals("[]", response.body().strip());
    }

    @Test
    void createsBooksWithDistinctIdsAndRetrievesThem() throws Exception {
        var created = request("POST", "/books", VALID);
        assertEquals(201, created.statusCode(), created.body());
        BookResponse first = json.readValue(created.body(), BookResponse.class);
        assertNotNull(first.id());
        assertEquals("Crime and Punishment", first.title());
        assertEquals("Fyodor Dostoevsky", first.author());
        assertEquals("Fiction", first.genre());
        assertEquals(350, first.pages());
        String location = created.headers().firstValue("Location").orElseThrow();
        assertEquals("/books/" + first.id(), location);
        var retrieved = request("GET", location, null);
        assertEquals(200, retrieved.statusCode());
        assertEquals(first, json.readValue(retrieved.body(), BookResponse.class));
        var duplicate = request("POST", "/books", VALID);
        assertEquals(201, duplicate.statusCode());
        BookResponse second = json.readValue(duplicate.body(), BookResponse.class);
        assertNotEquals(first.id(), second.id());
        assertArrayEquals(new BookResponse[]{first, second},
            json.readValue(request("GET", "/books", null).body(), BookResponse[].class));
    }

    @Test
    void rejectsInvalidRequestsWithoutAddingBooks() throws Exception {
        String[] invalidBodies = {
            "{}", "null", "{", VALID.replace("350", "0"), VALID.replace("350", "-10"),
            VALID.replace("350", "3.5"), VALID.replace("350", "2147483648"),
            VALID.replace("350", "null"), VALID.replace("350", "\"abc\""),
            VALID.replace("  Crime and Punishment  ", " "),
            VALID.replace("Fyodor Dostoevsky", " "), VALID.replace("Fiction", " "),
            "{\"title\":\"T\",\"author\":\"A\",\"genre\":\"G\"}",
            VALID.replace("  Crime and Punishment  ", "\\u2003")
        };
        for (String body : invalidBodies) {
            var response = request("POST", "/books", body);
            assertEquals(400, response.statusCode(), body + " -> " + response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("json"));
            assertEquals(400, json.readTree(response.body()).get("status").asInt());
        }
        assertEquals("[]", request("GET", "/books", null).body());
    }

    @Test
    void putReplacesAllFieldsPreservingIdentity() throws Exception {
        var created = request("POST", "/books", VALID);
        var original = json.readValue(created.body(), BookResponse.class);
        String path = "/books/" + original.id();
        String replacement = """
            {"title":"  The Idiot  ","author":"Dostoevsky","genre":"Literary fiction","pages":600}
            """;
        var response = request("PUT", path, replacement);
        assertEquals(200, response.statusCode(), response.body());
        var expected = new BookResponse(original.id(), "The Idiot", "Dostoevsky", "Literary fiction", 600);
        assertEquals(expected, json.readValue(response.body(), BookResponse.class));
        assertEquals(expected, json.readValue(request("GET", path, null).body(), BookResponse.class));
        assertArrayEquals(new BookResponse[]{expected},
            json.readValue(request("GET", "/books", null).body(), BookResponse[].class));
        assertEquals(expected, json.readValue(request("PUT", path, replacement).body(), BookResponse.class));
    }

    @Test
    void invalidPutDoesNotModifyExistingBook() throws Exception {
        var original = json.readValue(request("POST", "/books", VALID).body(), BookResponse.class);
        String path = "/books/" + original.id();
        for (String invalid : new String[]{"{}", "{", VALID.replace("350", "0"),
                VALID.replace("350", "3.5"), VALID.replace("Fyodor Dostoevsky", " ")}) {
            var response = request("PUT", path, invalid);
            assertEquals(400, response.statusCode(), response.body());
            assertEquals(original, json.readValue(request("GET", path, null).body(), BookResponse.class));
        }
    }

    @Test
    void putToUnknownIdReturns404WithoutCreatingBook() throws Exception {
        var response = request("PUT", "/books/" + UUID.randomUUID(), VALID);
        assertEquals(404, response.statusCode());
        assertEquals(400, request("PUT", "/books/not-a-uuid", VALID).statusCode());
        assertEquals("[]", request("GET", "/books", null).body());
    }

    @Test
    void distinguishesMissingBooksFromMalformedIds() throws Exception {
        var missing = request("GET", "/books/" + UUID.randomUUID(), null);
        assertEquals(404, missing.statusCode());
        assertEquals(404, json.readTree(missing.body()).get("status").asInt());
        var malformed = request("GET", "/books/not-a-uuid", null);
        assertEquals(400, malformed.statusCode());
        assertEquals(400, json.readTree(malformed.body()).get("status").asInt());
    }
}
