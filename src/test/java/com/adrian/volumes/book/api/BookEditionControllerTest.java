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
    private ApiClient client;
    private UUID bookId;
    private String path;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String ISBN = "9780140449242";
    private static final String BODY = "{\"isbn\":\"0-14-044924-8\"}";

    @BeforeEach
    void prepare() throws Exception {
        registration.register("covers@example.com", "edition test passphrase");
        client = new ApiClient(port);
        assertEquals(204, client.login("covers@example.com", "edition test passphrase").statusCode());
        bookId = catalog.create("The Brothers Karamazov", "Fyodor Dostoevsky", "Fiction", 1013).id();
        path = "/books/" + bookId + "/edition";
        when(provider.findEdition(ISBN)).thenReturn(new BookEdition(ISBN, "OL123M", 42L));
    }

    @AfterEach void close() { client.close(); }

    @Test
    void persistsEditionAcrossCatalogAndLibraryAndCanRemoveIt() throws Exception {
        var response = client.request("PUT", path, BODY);
        assertEquals(200, response.statusCode(), response.body());
        var saved = json.readValue(response.body(), BookResponse.class);
        assertEquals(ISBN, saved.edition().isbn());
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
    void failureNeverReplacesAnExistingEdition() throws Exception {
        assertEquals(200, client.request("PUT", path, BODY).statusCode());
        var before = catalog.findById(bookId).orElseThrow().edition();
        for (int status : new int[]{404, 502, 503}) {
            doThrow(new ResponseStatusException(HttpStatus.valueOf(status))).when(provider).findEdition("9780140449266");
            var failed = client.request("PUT", path, "{\"isbn\":\"9780140449266\"}");
            assertEquals(status, failed.statusCode(), failed.body());
            assertEquals(before, catalog.findById(bookId).orElseThrow().edition());
        }
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
        verifyNoInteractions(provider);
        assertNull(catalog.findById(bookId).orElseThrow().edition());
    }
}
