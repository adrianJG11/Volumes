package com.adrian.volumes.book.api;

import com.adrian.volumes.book.BookSuggestion;
import com.adrian.volumes.book.OpenLibraryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class BookSearchController {
    private final OpenLibraryClient openLibrary;

    public BookSearchController(OpenLibraryClient openLibrary) { this.openLibrary = openLibrary; }

    @GetMapping("/books/search")
    public List<BookSuggestion> search(@RequestParam("q") String query) {
        return openLibrary.search(query);
    }
}
