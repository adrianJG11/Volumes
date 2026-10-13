package com.adrian.biblioteca.book;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BookTest {
    @Test
    void normalizesTextWithoutRemovingInternalSpaces() {
        Book book = new Book("  Crime and Punishment  ", "\tFyodor Dostoevsky\n", " Fiction ", 350);
        assertEquals("Crime and Punishment", book.getTitle());
        assertEquals("Fyodor Dostoevsky", book.getAuthor());
        assertEquals("Fiction", book.getGenre());
        assertEquals(350, book.getPages());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingTextInEveryRequiredField(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> new Book(invalid, "Author", "Genre", 1));
        assertThrows(IllegalArgumentException.class, () -> new Book("Title", invalid, "Genre", 1));
        assertThrows(IllegalArgumentException.class, () -> new Book("Title", "Author", invalid, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonPositivePageCounts(int pages) {
        assertThrows(IllegalArgumentException.class, () -> new Book("Title", "Author", "Genre", pages));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void acceptsPositivePageBoundaries(int pages) {
        assertEquals(pages, new Book("Title", "Author", "Genre", pages).getPages());
    }
}
