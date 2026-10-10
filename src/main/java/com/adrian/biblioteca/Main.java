package com.adrian.biblioteca;

public class Main {
    public static void main(String[] args) {
        // Example page counts; actual counts depend on the edition.
        Book firstBook = new Book(
            "The Count of Monte Cristo",
            "Alexandre Dumas",
            "Historical fiction",
            1200
        );

        Book secondBook = new Book(
            "The Brothers Karamazov",
            "Fyodor Dostoevsky",
            "Philosophical fiction",
            800
        );

        System.out.println(firstBook.title + " — " + firstBook.author);
        System.out.println(secondBook.title + " — " + secondBook.author);
    }
}
