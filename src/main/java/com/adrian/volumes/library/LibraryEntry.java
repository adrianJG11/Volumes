package com.adrian.volumes.library;

import java.util.UUID;
import com.adrian.volumes.book.BookEdition;
import com.fasterxml.jackson.annotation.JsonInclude;

public record LibraryEntry(UUID bookId, String title, String author, String genre,
                           int pages, ReadingStatus status, Integer rating,
                           @JsonInclude(JsonInclude.Include.NON_NULL) BookEdition edition) {}
