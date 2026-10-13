package com.adrian.biblioteca.book;

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
