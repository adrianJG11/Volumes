package com.adrian.volumes.book;

/** Prefilled book fields from an Open Library search; any value may be missing and the reader can edit all of them. */
public record BookSuggestion(String title, String author, String genre, Integer pages, String isbn,
                             String coverUrl, Integer firstPublishYear) {}
