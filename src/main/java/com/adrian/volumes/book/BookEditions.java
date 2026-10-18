package com.adrian.volumes.book;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class BookEditions {
    private final BookRepository repository;
    private final OpenLibraryClient openLibrary;

    public BookEditions(BookRepository repository, OpenLibraryClient openLibrary) {
        this.repository = repository;
        this.openLibrary = openLibrary;
    }

    public BookCatalog.Entry associate(UUID id, String input) {
        String isbn = Isbn.normalize(input);
        var existing = repository.findById(id).orElseThrow(BookEditions::missingBook);
        // Repeating the same PUT is safe and needs no external request.
        if (existing.edition() != null && existing.edition().isbn().equals(isbn)) return existing;
        var edition = openLibrary.findEdition(isbn);
        return repository.setEdition(id, edition).orElseThrow(BookEditions::missingBook);
    }

    public void remove(UUID id) {
        repository.setEdition(id, null).orElseThrow(BookEditions::missingBook);
    }

    private static ResponseStatusException missingBook() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found.");
    }
}
