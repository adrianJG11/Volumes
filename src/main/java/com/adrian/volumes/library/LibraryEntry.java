package com.adrian.volumes.library;

import java.util.UUID;

public record LibraryEntry(UUID bookId, String title, String author, String genre,
                           int pages, ReadingStatus status, Integer rating) {}
