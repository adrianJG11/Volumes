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
    void responsesIncludeOptionalOwnerRatingAndStatusChangesPreserveIt() throws Exception {
        registration.register("alice@example.com", PASSWORD);
        var book = catalog.create("The Brothers Karamazov", "Fyodor Dostoevsky", "Fiction", 800);
        String path = "/me/library/" + book.id();
        try (var alice = new ApiClient(port); var bob = reader("bob@example.com")) {
            assertEquals(204, alice.login("alice@example.com", PASSWORD).statusCode());
            var added = alice.request("POST", "/me/library", body(book.id()));
            assertEquals(201, added.statusCode());
            var addedJson = json.readTree(added.body());
            assertTrue(addedJson.has("rating"));
            assertTrue(addedJson.get("rating").isNull());
            var listedJson = json.readTree(alice.request("GET", "/me/library", null).body()).get(0);
            assertTrue(listedJson.has("rating"));
            assertTrue(listedJson.get("rating").isNull());
            assertEquals(201, bob.request("POST", "/me/library", body(book.id())).statusCode());

            assertEquals(200, alice.request("PUT", path + "/rating", "{\"rating\":5}").statusCode());
            assertEquals(5, entries(alice)[0].rating());
            assertNull(entries(bob)[0].rating());
            var updated = alice.request("PATCH", path, "{\"status\":\"FINISHED\"}");
            assertEquals(200, updated.statusCode());
            var entry = json.readValue(updated.body(), LibraryEntry.class);
            assertEquals(ReadingStatus.FINISHED, entry.status());
            assertEquals(5, entry.rating());
            assertEquals(5, entries(alice)[0].rating());
            assertNull(entries(bob)[0].rating());

            assertEquals(204, alice.request("DELETE", path, null).statusCode());
            var readded = alice.request("POST", "/me/library", body(book.id()));
            assertEquals(201, readded.statusCode());
            var fresh = json.readValue(readded.body(), LibraryEntry.class);
            assertEquals(ReadingStatus.PENDING, fresh.status());
            assertNull(fresh.rating());
        }
    }

    @Test
    void ratingUpdatesValidateInputAndPreserveOtherEntryFields() throws Exception {
        var first = catalog.create("The Brothers Karamazov", "Fyodor Dostoevsky", "Fiction", 800);
        var second = catalog.create("The Count of Monte Cristo", "Alexandre Dumas", "Fiction", 1000);
        String path = "/me/library/" + first.id() + "/rating";
        try (var client = reader("reader@example.com")) {
            assertEquals(201, client.request("POST", "/me/library", body(first.id())).statusCode());
            assertEquals(201, client.request("POST", "/me/library", body(second.id())).statusCode());
            for (int rating = 1; rating <= 5; rating++) {
                var response = client.request("PUT", path, "{\"rating\":" + rating + "}");
                assertEquals(200, response.statusCode());
                var entry = json.readValue(response.body(), LibraryEntry.class);
                assertEquals(rating, entry.rating());
                assertEquals(ReadingStatus.PENDING, entry.status());
                assertEquals(first.id(), entry.bookId());
                assertEquals(first.book().getTitle(), entry.title());
            }
            var before = entries(client);
            var repeated = client.request("PUT", path, "{\"rating\":5}");
            assertEquals(200, repeated.statusCode());
            assertEquals(before[0], json.readValue(repeated.body(), LibraryEntry.class));
            for (String invalid : new String[] {
                    "{}", "null", "{", "{\"rating\":null}", "{\"rating\":0}", "{\"rating\":6}",
                    "{\"rating\":-1}", "{\"rating\":2.5}", "{\"rating\":\"abc\"}",
                    "{\"rating\":true}", "{\"rating\":[]}"}) {
                assertEquals(400, client.request("PUT", path, invalid).statusCode(), invalid);
                assertArrayEquals(before, entries(client));
            }
            assertArrayEquals(new UUID[] {first.id(), second.id()},
                java.util.Arrays.stream(entries(client)).map(LibraryEntry::bookId).toArray(UUID[]::new));
            assertNull(entries(client)[1].rating());
            assertEquals("The Brothers Karamazov", catalog.findById(first.id()).orElseThrow().book().getTitle());
        }
    }

    @Test
    void ratingsRequireSessionCsrfAndAnOwnedLibraryEntry() throws Exception {
        var owner = registration.register("owner@example.com", PASSWORD);
        var book = catalog.create("Title", "Author", "Genre", 100);
        library.add(owner.id(), book.id());
        String path = "/me/library/" + book.id() + "/rating";
        try (var anonymous = new ApiClient(port); var client = reader("reader@example.com")) {
            assertEquals(401, anonymous.request("PUT", path, "{\"rating\":5}").statusCode());
            String forged = "{\"rating\":5,\"userId\":\"" + owner.id() + "\"}";
            assertEquals(404, client.request("PUT", path, forged).statusCode());
            assertEquals(0, entries(client).length);
            assertNull(library.list(owner.id()).getFirst().rating());
            assertEquals(404, client.request("PUT", "/me/library/" + UUID.randomUUID() + "/rating", "{\"rating\":5}").statusCode());
            assertEquals(400, client.request("PUT", "/me/library/not-a-uuid/rating", "{\"rating\":5}").statusCode());
            assertEquals(201, client.request("POST", "/me/library", body(book.id())).statusCode());
            assertEquals(403, client.send("PUT", path, "{\"rating\":5}", "application/json", null).statusCode());
            assertNull(entries(client)[0].rating());
            assertEquals(200, client.request("PUT", path, forged).statusCode());
            assertEquals(5, entries(client)[0].rating());
            assertNull(library.list(owner.id()).getFirst().rating());
        }
    }

    @Test
    void clearingRatingsIsIdempotentOwnerScopedAndPreservesOtherFields() throws Exception {
        var first = catalog.create("The Brothers Karamazov", "Fyodor Dostoevsky", "Fiction", 800);
        var second = catalog.create("The Count of Monte Cristo", "Alexandre Dumas", "Fiction", 1000);
        String path = "/me/library/" + first.id() + "/rating";
        try (var anonymous = new ApiClient(port); var alice = reader("alice@example.com");
             var bob = reader("bob@example.com")) {
            assertEquals(201, alice.request("POST", "/me/library", body(first.id())).statusCode());
            assertEquals(201, alice.request("POST", "/me/library", body(second.id())).statusCode());
            assertEquals(201, bob.request("POST", "/me/library", body(first.id())).statusCode());
            assertEquals(200, alice.request("PATCH", "/me/library/" + first.id(), "{\"status\":\"READING\"}").statusCode());
            assertEquals(200, alice.request("PUT", path, "{\"rating\":4}").statusCode());
            assertEquals(200, alice.request("PUT", "/me/library/" + second.id() + "/rating", "{\"rating\":2}").statusCode());
            assertEquals(200, bob.request("PUT", path, "{\"rating\":1}").statusCode());

            assertEquals(401, anonymous.request("DELETE", path, null).statusCode());
            assertEquals(403, alice.send("DELETE", path, null, "application/json", null).statusCode());
            assertEquals(4, entries(alice)[0].rating());

            assertEquals(204, alice.request("DELETE", path, null).statusCode());
            var cleared = entries(alice);
            assertNull(cleared[0].rating());
            assertEquals(ReadingStatus.READING, cleared[0].status());
            assertArrayEquals(new UUID[] {first.id(), second.id()},
                java.util.Arrays.stream(cleared).map(LibraryEntry::bookId).toArray(UUID[]::new));
            assertEquals(2, cleared[1].rating());
            assertEquals(1, entries(bob)[0].rating());
            assertTrue(catalog.findById(first.id()).isPresent());

            assertEquals(204, alice.request("DELETE", path, null).statusCode());
            assertArrayEquals(cleared, entries(alice));

            String unowned = "/me/library/" + second.id() + "/rating";
            assertEquals(404, bob.request("DELETE", unowned, null).statusCode());
            assertEquals(2, entries(alice)[1].rating());
            assertEquals(404, bob.request("DELETE", "/me/library/" + UUID.randomUUID() + "/rating", null).statusCode());
            assertEquals(400, bob.request("DELETE", "/me/library/not-a-uuid/rating", null).statusCode());
            assertEquals(1, entries(bob).length);
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
