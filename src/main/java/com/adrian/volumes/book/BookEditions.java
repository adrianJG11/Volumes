package com.adrian.volumes.book;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class BookEditions {
    private final BookRepository repository;
    private final OpenLibraryClient openLibrary;
    private final ItunesClient itunes;

    public BookEditions(BookRepository repository, OpenLibraryClient openLibrary, ItunesClient itunes) {
        this.repository = repository;
        this.openLibrary = openLibrary;
        this.itunes = itunes;
    }

    public BookCatalog.Entry associate(UUID id, String input) {
        String isbn = Isbn.normalize(input);
        var existing = repository.findById(id).orElseThrow(BookEditions::missingBook);
        // Reuse a resolved cover, but let a previously missing cover be looked up again.
        if (existing.edition() != null && existing.edition().isbn().equals(isbn)
                && existing.edition().coverUrl() != null) return existing;
        var edition = openLibraryEdition(isbn);
        if (edition.coverUrl() == null) edition = edition.withCover(itunes.findCover(isbn));
        return repository.setEdition(id, edition).orElseThrow(BookEditions::missingBook);
    }

    /**
     * Open Library supplies the edition link and usually the cover. A valid ISBN is still saved when it does
     * not know it (404) or is down (502/503); iTunes may have the cover. Our own rate limit (429) is the
     * exception: the client retries shortly instead of losing Open Library's data.
     */
    private BookEdition openLibraryEdition(String isbn) {
        try {
            return openLibrary.findEdition(isbn);
        } catch (ResponseStatusException failure) {
            if (failure.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) throw failure;
            return new BookEdition(isbn, null, null);
        }
    }

    public void remove(UUID id) {
        repository.setEdition(id, null).orElseThrow(BookEditions::missingBook);
    }

    private static ResponseStatusException missingBook() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found.");
    }
}
