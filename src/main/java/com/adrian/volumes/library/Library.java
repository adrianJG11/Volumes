package com.adrian.volumes.library;

import com.adrian.volumes.book.Book;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Library {
    private final List<Book> books = new ArrayList<>();

    public void addBook(Book book) {
        books.add(Objects.requireNonNull(book, "Book must not be null."));
    }

    public List<Book> listBooks() {
        return List.copyOf(books);
    }
}
