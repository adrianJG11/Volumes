package com.adrian.volumes.library;

import java.util.UUID;

/** A reader's library entry; {@code rating} is null until the reader rates the book. */
public record LibraryEntry(UUID bookId, String title, String author, String genre,
                           int pages, ReadingStatus status, Integer rating) {}
