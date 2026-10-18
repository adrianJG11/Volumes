package com.adrian.volumes.book;

import com.fasterxml.jackson.annotation.JsonProperty;

/** An edition chosen by ISBN. Open Library may not know it; the cover may come from any allowed provider. */
public record BookEdition(String isbn, String openLibraryId, String coverUrl) {
    public BookEdition {
        isbn = Isbn.normalize(isbn);
        if (openLibraryId != null && !openLibraryId.matches("OL[0-9]+M"))
            throw new IllegalArgumentException("Invalid Open Library edition ID.");
        if (coverUrl != null && !CoverUrls.allowed(coverUrl)) throw new IllegalArgumentException("Invalid cover URL.");
    }

    public BookEdition withCover(String url) { return new BookEdition(isbn, openLibraryId, url); }

    @JsonProperty(value = "sourceUrl", access = JsonProperty.Access.READ_ONLY)
    public String sourceUrl() { return openLibraryId == null ? null : "https://openlibrary.org/books/" + openLibraryId; }
}
