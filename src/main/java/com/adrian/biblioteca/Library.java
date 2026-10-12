package com.adrian.biblioteca;

import com.adrian.biblioteca.book.Book;

import java.util.ArrayList;
import java.util.List;

public class Library {
    private List<Book> books = new ArrayList<>();

    public void addBook(Book book) {
        books.add(book);
    }

    public void listBooks() {
        for (Book book : books) {
            IO.println(book.getTitle() + " — " + book.getAuthor());
        }
    }
}
