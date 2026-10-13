package com.adrian.volumes.book.api;

import com.adrian.volumes.book.BookCatalog;
import java.util.UUID;

public record BookResponse(UUID id, String title, String author, String genre, int pages) {
    public static BookResponse from(BookCatalog.Entry entry) {
        var book = entry.book();
        return new BookResponse(entry.id(), book.getTitle(), book.getAuthor(), book.getGenre(), book.getPages());
    }
}
