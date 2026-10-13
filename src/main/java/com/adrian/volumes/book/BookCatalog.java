package com.adrian.volumes.book;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookCatalog {
    public record Entry(UUID id, Book book) {}

    private final Map<UUID, Entry> books = new LinkedHashMap<>();

    public synchronized Entry create(String title, String author, String genre, int pages) {
        Book book = new Book(title, author, genre, pages);
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (books.containsKey(id));
        Entry entry = new Entry(id, book);
        books.put(id, entry);
        return entry;
    }

    public synchronized List<Entry> listBooks() {
        return List.copyOf(books.values());
    }

    public synchronized Optional<Entry> update(UUID id, String title, String author, String genre, int pages) {
        if (!books.containsKey(id)) {
            return Optional.empty();
        }
        Book replacement = new Book(title, author, genre, pages);
        Entry entry = new Entry(id, replacement);
        books.put(id, entry);
        return Optional.of(entry);
    }

    public synchronized Optional<Entry> findById(UUID id) {
        return Optional.ofNullable(books.get(id));
    }
}
