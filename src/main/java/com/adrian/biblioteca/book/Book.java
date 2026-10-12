package com.adrian.biblioteca.book;

public class Book {
    private final String title;
    private final String author;
    private final String genre;
    private final int pages;

    public Book(String title, String author, String genre, int pages) {
        this.title = title;
        this.author = author;
        this.genre = genre;
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
