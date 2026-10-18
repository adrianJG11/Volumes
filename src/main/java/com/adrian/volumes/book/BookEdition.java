package com.adrian.volumes.book;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BookEdition(String isbn, String openLibraryId, Long coverId) {
    public BookEdition {
        isbn = Isbn.normalize(isbn);
        if (openLibraryId == null || !openLibraryId.matches("OL[0-9]+M"))
            throw new IllegalArgumentException("Invalid Open Library edition ID.");
        if (coverId != null && coverId <= 0) throw new IllegalArgumentException("Invalid cover ID.");
    }

    @JsonProperty(value = "coverUrl", access = JsonProperty.Access.READ_ONLY)
    public String coverUrl() {
        return coverId == null ? null : "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg?default=false";
    }

    @JsonProperty(value = "sourceUrl", access = JsonProperty.Access.READ_ONLY)
    public String sourceUrl() { return "https://openlibrary.org/books/" + openLibraryId; }
}
