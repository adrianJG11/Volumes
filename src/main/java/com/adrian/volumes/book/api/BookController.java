package com.adrian.volumes.book.api;

import com.adrian.volumes.book.BookCatalog;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/books")
public class BookController {
    private final BookCatalog catalog;

    public BookController(BookCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<BookResponse> listBooks() {
        return catalog.listBooks().stream().map(BookResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
        var entry = catalog.create(request.title(), request.author(), request.genre(), request.pages());
        return ResponseEntity.created(URI.create("/books/" + entry.id())).body(BookResponse.from(entry));
    }

    @PutMapping("/{id}")
    public BookResponse updateBook(@PathVariable UUID id, @Valid @RequestBody BookRequest request) {
        return catalog.update(id, request.title(), request.author(), request.genre(), request.pages())
            .map(BookResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
    }

    @GetMapping("/{id}")
    public BookResponse getBook(@PathVariable UUID id) {
        return catalog.findById(id).map(BookResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
    }
}
