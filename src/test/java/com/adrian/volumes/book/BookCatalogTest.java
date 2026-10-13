package com.adrian.volumes.book;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BookCatalogTest {
    @Test
    void startsEmptyAndReturnsProtectedSnapshots() {
        BookCatalog catalog = new BookCatalog();
        assertTrue(catalog.listBooks().isEmpty());
        var first = catalog.create("First", "Author", "Fiction", 100);
        var snapshot = catalog.listBooks();
        var second = catalog.create("Second", "Author", "Fiction", 200);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEquals(List.of(first), snapshot);
        assertEquals(List.of(first, second), catalog.listBooks());
        assertEquals(first, catalog.findById(first.id()).orElseThrow());
        assertTrue(catalog.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rejectsInvalidDomainDataBeforeStoringAnything() {
        BookCatalog catalog = new BookCatalog();
        assertThrows(IllegalArgumentException.class, () -> catalog.create(" ", "Author", "Fiction", 1));
        assertThrows(IllegalArgumentException.class, () -> catalog.create("Title", "Author", "Fiction", 0));
        assertTrue(catalog.listBooks().isEmpty());
    }

    @Test
    void replacesFieldsButPreservesIdOrderAndEarlierSnapshots() {
        BookCatalog catalog = new BookCatalog();
        var first = catalog.create("First", "Author", "Fiction", 100);
        var second = catalog.create("Second", "Author", "Fiction", 200);
        var before = catalog.listBooks();
        var updated = catalog.update(first.id(), " Updated ", "New Author", "History", 300).orElseThrow();
        assertEquals(first.id(), updated.id());
        assertEquals("Updated", updated.book().getTitle());
        assertEquals("New Author", updated.book().getAuthor());
        assertEquals("History", updated.book().getGenre());
        assertEquals(300, updated.book().getPages());
        assertEquals(List.of(updated, second), catalog.listBooks());
        assertEquals("First", before.getFirst().book().getTitle());
    }

    @Test
    void invalidUpdateLeavesExistingBookUnchanged() {
        BookCatalog catalog = new BookCatalog();
        var original = catalog.create("Title", "Author", "Genre", 100);
        assertThrows(IllegalArgumentException.class,
            () -> catalog.update(original.id(), "Changed", "Author", "Genre", 0));
        assertEquals(original, catalog.findById(original.id()).orElseThrow());
    }

    @Test
    void updatingUnknownIdDoesNotCreateABook() {
        BookCatalog catalog = new BookCatalog();
        assertTrue(catalog.update(UUID.randomUUID(), "Title", "Author", "Genre", 100).isEmpty());
        assertTrue(catalog.listBooks().isEmpty());
    }

    @Test
    void concurrentCreatesKeepEveryBookWithItsOwnId() throws Exception {
        BookCatalog catalog = new BookCatalog();
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
