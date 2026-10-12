package com.adrian.biblioteca;

import com.adrian.biblioteca.book.Book;

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

        String title = IO.readln("Book title: ");
        String author = IO.readln("Book author: ");
        String genre = IO.readln("Book genre: ");
        int pages;

        while (true) {
            String pagesInput = IO.readln("Page count: ");
            if (pagesInput == null) {
                IO.println("Input ended. No book was added.");
                return;
            }

            try {
                pages = Integer.parseInt(pagesInput);
                if (pages <= 0) {
                    IO.println("Page count must be greater than zero.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                IO.println("Invalid page count. Please enter a whole number, such as 350.");
            }
        }

        Book enteredBook = new Book(title, author, genre, pages);

        Library library = new Library();

        IO.println("Empty library:");
        library.listBooks();
        IO.println("End of empty library check");

        library.addBook(firstBook);
        library.addBook(secondBook);
        library.addBook(enteredBook);
        library.listBooks();
    }

}
