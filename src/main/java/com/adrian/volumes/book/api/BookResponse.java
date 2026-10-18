package com.adrian.volumes.book.api;

import com.adrian.volumes.book.BookCatalog;
import com.adrian.volumes.book.BookEdition;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public record BookResponse(UUID id, String title, String author, String genre, int pages,
                           @JsonInclude(JsonInclude.Include.NON_NULL) BookEdition edition) {
    public BookResponse(UUID id, String title, String author, String genre, int pages) {
        this(id, title, author, genre, pages, null);
    }
    public static BookResponse from(BookCatalog.Entry entry) {
        var book = entry.book();
        return new BookResponse(entry.id(), book.getTitle(), book.getAuthor(), book.getGenre(), book.getPages(), entry.edition());
    }
}
