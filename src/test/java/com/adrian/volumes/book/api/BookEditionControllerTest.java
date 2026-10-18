package com.adrian.volumes.book.api;

import com.adrian.volumes.book.*;
import com.adrian.volumes.support.ApiClient;
import com.adrian.volumes.user.UserRegistration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookEditionControllerTest {
    @LocalServerPort int port;
    @Autowired UserRegistration registration;
    @Autowired BookCatalog catalog;
    @MockitoBean OpenLibraryClient provider;
    @MockitoBean ItunesClient itunes;
    private ApiClient client;
    private UUID bookId;
    private String path;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String ISBN = "9780140449242";
    private static final String COVER = "https://covers.openlibrary.org/b/id/42-M.jpg?default=false";
    private static final String BODY = "{\"isbn\":\"0-14-044924-8\"}";

    @BeforeEach
    void prepare() throws Exception {
        registration.register("covers@example.com", "edition test passphrase");
        client = new ApiClient(port);
        assertEquals(204, client.login("covers@example.com", "edition test passphrase").statusCode());
        bookId = catalog.create("The Brothers Karamazov", "Fyodor Dostoevsky", "Fiction", 1013).id();
        path = "/books/" + bookId + "/edition";
        when(provider.findEdition(ISBN)).thenReturn(new BookEdition(ISBN, "OL123M", COVER));
    }

    @AfterEach void close() { client.close(); }

    @Test
    void persistsEditionAcrossCatalogAndLibraryAndCanRemoveIt() throws Exception {
        var response = client.request("PUT", path, BODY);
        assertEquals(200, response.statusCode(), response.body());
        var saved = json.readValue(response.body(), BookResponse.class);
        assertEquals(new BookEdition(ISBN, "OL123M", COVER), saved.edition());
        assertEquals("https://openlibrary.org/books/OL123M", saved.edition().sourceUrl());
        verifyNoInteractions(itunes); // Open Library already had a cover
        assertEquals("The Brothers Karamazov", saved.title());
        assertEquals(saved, json.readValue(client.request("GET", "/books/" + bookId, null).body(), BookResponse.class));
        assertEquals(saved, json.readValue(client.request("GET", "/books", null).body(), BookResponse[].class)[0]);
        assertEquals(200, client.request("PUT", path, "{\"isbn\":\"9780140449242\"}").statusCode());
        verify(provider, times(1)).findEdition(ISBN);
        var added = client.request("POST", "/me/library", "{\"bookId\":\"" + bookId + "\"}");
        assertEquals(201, added.statusCode(), added.body());
        var library = json.readTree(client.request("GET", "/me/library", null).body());
        assertEquals(ISBN, library.get(0).path("edition").path("isbn").asString());
        assertEquals(204, client.request("DELETE", path, null).statusCode());
        assertEquals(204, client.request("DELETE", path, null).statusCode());
        assertNull(catalog.findById(bookId).orElseThrow().edition());
    }

    @Test
    void savesIsbnsOpenLibraryDoesNotKnowWithACoverFromAnotherSource() throws Exception {
        String other = "9788439710349", artwork = "https://is1-ssl.mzstatic.com/image/thumb/a/600x600bb.jpg";
        for (int status : new int[]{404, 502, 503}) {
            doThrow(new ResponseStatusException(HttpStatus.valueOf(status))).when(provider).findEdition(other);
            when(itunes.findCover(other)).thenReturn(artwork);
            assertEquals(200, client.request("PUT", path, BODY).statusCode());
            var saved = client.request("PUT", path, "{\"isbn\":\"" + other + "\"}");
            assertEquals(200, saved.statusCode(), saved.body());
            var edition = json.readValue(saved.body(), BookResponse.class).edition();
            assertEquals(new BookEdition(other, null, artwork), edition);
            assertFalse(json.readTree(saved.body()).path("edition").has("coverId"));
            assertTrue(json.readTree(saved.body()).path("edition").path("sourceUrl").isNull());
        }
    }

    @Test
    void sameIsbnCanRecoverACoverAfterProviderFailure() throws Exception {
        when(provider.findEdition(ISBN))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
            .thenReturn(new BookEdition(ISBN, "OL123M", COVER));
        var first = client.request("PUT", path, BODY);
        assertEquals(200, first.statusCode(), first.body());
        assertNull(json.readValue(first.body(), BookResponse.class).edition().coverUrl());

        var retry = client.request("PUT", path, BODY);
        assertEquals(200, retry.statusCode(), retry.body());
        assertEquals(COVER, json.readValue(retry.body(), BookResponse.class).edition().coverUrl());
        assertEquals(COVER, catalog.findById(bookId).orElseThrow().edition().coverUrl());
        verify(provider, times(2)).findEdition(ISBN);
    }

    @Test
    void rateLimitedLookupsNeverReplaceAnExistingEdition() throws Exception {
        assertEquals(200, client.request("PUT", path, BODY).statusCode());
        var before = catalog.findById(bookId).orElseThrow().edition();
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(provider).findEdition("9780140449266");
        var failed = client.request("PUT", path, "{\"isbn\":\"9780140449266\"}");
        assertEquals(429, failed.statusCode(), failed.body());
        assertEquals(before, catalog.findById(bookId).orElseThrow().edition());
    }

    @Test
    void rejectsInvalidMissingAndUnauthenticatedRequests() throws Exception {
        for (String body : new String[]{"{}", "null", "{", "{\"isbn\":\"abc\"}", "{\"isbn\":\"9780140449243\"}"})
            assertEquals(400, client.request("PUT", path, body).statusCode(), body);
        assertEquals(404, client.request("PUT", "/books/" + UUID.randomUUID() + "/edition", BODY).statusCode());
        assertEquals(404, client.request("DELETE", "/books/" + UUID.randomUUID() + "/edition", null).statusCode());
        assertEquals(403, client.send("PUT", path, BODY, "application/json", null).statusCode());
        try (var anonymous = new ApiClient(port)) {
            assertEquals(401, anonymous.request("PUT", path, BODY).statusCode());
            assertEquals(401, anonymous.request("DELETE", path, null).statusCode());
        }
        verifyNoInteractions(provider, itunes);
        assertNull(catalog.findById(bookId).orElseThrow().edition());
    }
}
