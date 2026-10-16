package com.adrian.volumes.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookCatalogTest {
    @Autowired private BookCatalog catalog;

    private void assertEntry(BookCatalog.Entry expected, BookCatalog.Entry actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.book().getTitle(), actual.book().getTitle());
        assertEquals(expected.book().getAuthor(), actual.book().getAuthor());
        assertEquals(expected.book().getGenre(), actual.book().getGenre());
        assertEquals(expected.book().getPages(), actual.book().getPages());
    }

    private void assertEntries(List<BookCatalog.Entry> expected, List<BookCatalog.Entry> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEntry(expected.get(i), actual.get(i));
        }
    }

    @Test
    void startsEmptyAndReturnsProtectedSnapshots() {
        assertTrue(catalog.listBooks().isEmpty());
        var first = catalog.create("First", "Author", "Fiction", 100);
        var snapshot = catalog.listBooks();
        var second = catalog.create("Second", "Author", "Fiction", 200);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEntries(List.of(first), snapshot);
        assertEntries(List.of(first, second), catalog.listBooks());
        assertEntry(first, catalog.findById(first.id()).orElseThrow());
        assertTrue(catalog.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rejectsInvalidDomainDataBeforeStoringAnything() {
        assertThrows(IllegalArgumentException.class, () -> catalog.create(" ", "Author", "Fiction", 1));
        assertThrows(IllegalArgumentException.class, () -> catalog.create("Title", "Author", "Fiction", 0));
        assertTrue(catalog.listBooks().isEmpty());
    }

    @Test
    void replacesFieldsButPreservesIdOrderAndEarlierSnapshots() {
        var first = catalog.create("First", "Author", "Fiction", 100);
        var second = catalog.create("Second", "Author", "Fiction", 200);
        var before = catalog.listBooks();
        var updated = catalog.update(first.id(), " Updated ", "New Author", "History", 300).orElseThrow();
        assertEquals(first.id(), updated.id());
        assertEquals("Updated", updated.book().getTitle());
        assertEquals("New Author", updated.book().getAuthor());
        assertEquals("History", updated.book().getGenre());
        assertEquals(300, updated.book().getPages());
        assertEntries(List.of(updated, second), catalog.listBooks());
        assertEquals("First", before.getFirst().book().getTitle());
    }

    @Test
    void invalidUpdateLeavesExistingBookUnchanged() {
        var original = catalog.create("Title", "Author", "Genre", 100);
        assertThrows(IllegalArgumentException.class,
            () -> catalog.update(original.id(), "Changed", "Author", "Genre", 0));
        assertEntry(original, catalog.findById(original.id()).orElseThrow());
    }

    @Test
    void updatingUnknownIdDoesNotCreateABook() {
        assertTrue(catalog.update(UUID.randomUUID(), "Title", "Author", "Genre", 100).isEmpty());
        assertTrue(catalog.listBooks().isEmpty());
    }

    @Test
    void storesSqlLikeTextAsData() {
        var created = catalog.create("Reader's choice'); DROP TABLE books; --", "Author", "Fiction", 100);
        assertEntry(created, catalog.findById(created.id()).orElseThrow());
        assertEquals(1, catalog.listBooks().size());
    }

    @Test
    void concurrentCreatesKeepEveryBookWithItsOwnId() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BookCatalog.Entry>> tasks = IntStream.range(0, 50)
                .mapToObj(i -> executor.submit(() -> catalog.create("Title", "Author", "Fiction", 100)))
                .toList();
            var ids = new HashSet<UUID>();
            for (var task : tasks) {
                assertTrue(ids.add(task.get().id()));
            }
            assertEquals(50, catalog.listBooks().size());
        }
    }
}
