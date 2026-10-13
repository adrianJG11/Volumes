package com.adrian.volumes.library;

import com.adrian.volumes.book.Book;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryTest {
    @Test
    void startsEmpty() {
        assertTrue(new Library().listBooks().isEmpty());
    }

    @Test
    void preservesInsertionOrderAndAllowsDuplicates() {
        Library library = new Library();
        Book first = new Book("First", "Author", "Fiction", 100);
        Book second = new Book("Second", "Author", "Fiction", 200);
        library.addBook(first);
        library.addBook(second);
        library.addBook(first);
        assertEquals(List.of(first, second, first), library.listBooks());
    }

    @Test
    void rejectsNullWithoutChangingTheCollection() {
        Library library = new Library();
        assertThrows(NullPointerException.class, () -> library.addBook(null));
        assertTrue(library.listBooks().isEmpty());
    }

    @Test
    void snapshotCannotModifyLibraryAndDoesNotTrackLaterAdditions() {
        Library library = new Library();
        Book first = new Book("First", "Author", "Fiction", 100);
        Book second = new Book("Second", "Author", "Fiction", 200);
        library.addBook(first);
        List<Book> snapshot = library.listBooks();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.set(0, second));
        assertEquals(List.of(first), library.listBooks());
        library.addBook(second);
        assertEquals(List.of(first), snapshot);
        assertEquals(List.of(first, second), library.listBooks());
    }
}
