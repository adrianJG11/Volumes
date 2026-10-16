package com.adrian.volumes.book;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookCatalog {
    public record Entry(UUID id, Book book) {}

    private final BookRepository repository;

    public BookCatalog(BookRepository repository) {
        this.repository = repository;
    }

    public Entry create(String title, String author, String genre, int pages) {
        Book book = new Book(title, author, genre, pages);
        return repository.insert(UUID.randomUUID(), book);
    }

    public List<Entry> listBooks() {
        return repository.findAll();
    }

    public Optional<Entry> update(UUID id, String title, String author, String genre, int pages) {
        Book replacement = new Book(title, author, genre, pages);
        return repository.update(id, replacement);
    }

    public Optional<Entry> findById(UUID id) {
        return repository.findById(id);
    }
}
