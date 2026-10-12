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

        String title = IO.readln("Book title: ");
        String author = IO.readln("Book author: ");
        String genre = IO.readln("Book genre: ");
        String pagesInput = IO.readln("Page count: ");
        int pages;
        try {
            pages = Integer.parseInt(pagesInput);
        } catch (NumberFormatException e) {
            IO.println("Invalid page count. Please enter a whole number, such as 350.");
            return;
        }

        IO.println(title + " — " + author);

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
