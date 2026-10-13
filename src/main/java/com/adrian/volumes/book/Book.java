package com.adrian.volumes.book;

public class Book {
    private final String title;
    private final String author;
    private final String genre;
    private final int pages;

    public Book(String title, String author, String genre, int pages) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank.");
        }
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("Author must not be blank.");
        }
        if (genre == null || genre.isBlank()) {
            throw new IllegalArgumentException("Genre must not be blank.");
        }
        if (pages <= 0) {
            throw new IllegalArgumentException("Page count must be greater than zero.");
        }

        this.title = title.strip();
        this.author = author.strip();
        this.genre = genre.strip();
        this.pages = pages;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getGenre() {
        return genre;
    }

    public int getPages() {
        return pages;
    }
}
