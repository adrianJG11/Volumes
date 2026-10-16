package com.adrian.volumes.library;

import com.adrian.volumes.book.BookCatalog;
import com.adrian.volumes.support.ApiClient;
import com.adrian.volumes.user.UserRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PersonalLibraryTest {
    @LocalServerPort int port;
    @Autowired UserRegistration registration;
    @Autowired BookCatalog catalog;
    @Autowired PersonalLibraryRepository library;
    @Autowired JdbcClient jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PASSWORD = "a personal library passphrase";

    private ApiClient reader(String email) throws Exception {
        registration.register(email, PASSWORD);
        var client = new ApiClient(port);
        assertEquals(204, client.login(email, PASSWORD).statusCode());
        return client;
    }

    private String body(UUID id) { return "{\"bookId\":\"" + id + "\"}"; }

    private LibraryEntry[] entries(ApiClient client) throws Exception {
        var response = client.request("GET", "/me/library", null);
        assertEquals(200, response.statusCode());
        return json.readValue(response.body(), LibraryEntry[].class);
    }

    @Test
    void independentLibrariesPreserveOtherReadersAndSharedBooks() throws Exception {
        var first = catalog.create("The Brothers Karamazov", "Fyodor Dostoevsky", "Fiction", 800);
        var second = catalog.create("The Count of Monte Cristo", "Alexandre Dumas", "Fiction", 1000);
        String path = "/me/library/" + first.id();
        try (var alice = reader("alice@example.com"); var bob = reader("bob@example.com")) {
            assertEquals(0, entries(alice).length);
            assertEquals(201, alice.request("POST", "/me/library", body(first.id())).statusCode());
            assertEquals(201, alice.request("POST", "/me/library", body(second.id())).statusCode());
            assertEquals(ReadingStatus.PENDING, entries(alice)[0].status());
            assertEquals(0, entries(bob).length);
            assertEquals(404, bob.request("PATCH", path, "{\"status\":\"FINISHED\"}").statusCode());
            assertEquals(404, bob.request("DELETE", path, null).statusCode());
            assertEquals(201, bob.request("POST", "/me/library", body(first.id())).statusCode());
            assertEquals(200, alice.request("PATCH", path, "{\"status\":\"FINISHED\"}").statusCode());
            assertEquals(200, bob.request("PATCH", path, "{\"status\":\"READING\"}").statusCode());
            var a = entries(alice);
            assertEquals(first.id(), a[0].bookId());
            assertEquals(second.id(), a[1].bookId());
            assertEquals(ReadingStatus.FINISHED, a[0].status());
            assertEquals(ReadingStatus.READING, entries(bob)[0].status());
            catalog.update(first.id(), "Updated shared title", "Author", "Fiction", 900);
            assertEquals("Updated shared title", entries(bob)[0].title());
            assertEquals(204, alice.request("DELETE", path, null).statusCode());
            assertEquals(1, entries(alice).length);
            assertEquals(1, entries(bob).length);
            assertTrue(catalog.findById(first.id()).isPresent());
            assertEquals(404, alice.request("DELETE", path, null).statusCode());
        }
    }

    @Test
    void validatesRequestsAndIgnoresClientSuppliedOwner() throws Exception {
        var victim = registration.register("victim@example.com", PASSWORD);
        var book = catalog.create("Title", "Author", "Genre", 100);
        try (var client = reader("reader@example.com")) {
            String malicious = "{\"bookId\":\"" + book.id() + "\",\"userId\":\"" + victim.id() + "\"}";
            assertEquals(201, client.request("POST", "/me/library", malicious).statusCode());
            assertTrue(library.list(victim.id()).isEmpty());
            assertEquals(1, entries(client).length);
            assertEquals(409, client.request("POST", "/me/library", body(book.id())).statusCode());
            assertEquals(404, client.request("POST", "/me/library", body(UUID.randomUUID())).statusCode());
            for (String invalid : new String[] {"{}", "null", "{", "{\"bookId\":\"bad-id\"}"}) {
                assertEquals(400, client.request("POST", "/me/library", invalid).statusCode());
            }
            for (String invalid : new String[] {"{}", "null", "{", "{\"status\":null}", "{\"status\":\"INVALID\"}", "{\"status\":0}"}) {
                assertEquals(400, client.request("PATCH", "/me/library/" + book.id(), invalid).statusCode());
                assertEquals(ReadingStatus.PENDING, entries(client)[0].status());
            }
            assertEquals(400, client.request("DELETE", "/me/library/not-a-uuid", null).statusCode());
        }
    }

    @Test
    void unauthenticatedAndMissingCsrfRequestsCannotChangeEntries() throws Exception {
        var book = catalog.create("Title", "Author", "Genre", 100);
        try (var anonymous = new ApiClient(port)) {
            assertEquals(401, anonymous.request("GET", "/me/library", null).statusCode());
            assertEquals(401, anonymous.request("POST", "/me/library", body(book.id())).statusCode());
            assertEquals(401, anonymous.request("PATCH", "/me/library/" + book.id(), "{\"status\":\"READING\"}").statusCode());
            assertEquals(401, anonymous.request("DELETE", "/me/library/" + book.id(), null).statusCode());
        }
        try (var client = reader("reader@example.com")) {
            assertEquals(403, client.send("POST", "/me/library", body(book.id()), "application/json", null).statusCode());
            assertEquals(0, entries(client).length);
        }
    }

    @Test
    void databaseEnforcesForeignKeysStatusAndConcurrentUniqueness() throws Exception {
        var user = registration.register("reader@example.com", PASSWORD);
        var book = catalog.create("Title", "Author", "Genre", 100);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> library.add(user.id(), book.id()).isPresent())).toList();
            int inserted = 0;
            for (Future<Boolean> task : tasks) if (task.get()) inserted++;
            assertEquals(1, inserted);
        }
        assertThrows(DataIntegrityViolationException.class, () -> library.add(user.id(), UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class, () -> library.add(UUID.randomUUID(), book.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE library_entries SET status = 'WRONG'").update());
        assertEquals(ReadingStatus.PENDING, library.list(user.id()).getFirst().status());
    }
}
