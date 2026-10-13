package com.adrian.biblioteca.cli;

import com.adrian.biblioteca.book.Book;
import com.adrian.biblioteca.library.Library;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LibraryConsole {
    private final Library library;
    private final Supplier<String> input;
    private final Consumer<String> output;

    public LibraryConsole(Library library, Supplier<String> input, Consumer<String> output) {
        this.library = Objects.requireNonNull(library);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
    }

    public void run() {
        while (true) {
            output.accept("1. Add book");
            output.accept("2. List books");
            output.accept("0. Exit");
            String choice = readLine("Choose an option: ");
            if (choice == null) {
                return;
            }

            switch (choice.strip()) {
                case "1" -> {
                    if (!addBook()) {
                        return;
                    }
                }
                case "2" -> displayBooks();
                case "0" -> {
                    output.accept("Goodbye!");
                    return;
                }
                default -> output.accept("Invalid option. Choose 1, 2, or 0.");
            }
        }
    }

    private boolean addBook() {
        String title = readRequiredText("Book title: ");
        if (title == null) {
            return false;
        }
        String author = readRequiredText("Book author: ");
        if (author == null) {
            return false;
        }
        String genre = readRequiredText("Book genre: ");
        if (genre == null) {
            return false;
        }
        Integer pages = readPositivePages();
        if (pages == null) {
            return false;
        }

        library.addBook(new Book(title, author, genre, pages));
        output.accept("Book added.");
        return true;
    }

    private String readRequiredText(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value == null) {
                return null;
            }
            if (value.isBlank()) {
                output.accept("This field must not be blank.");
                continue;
            }
            return value.strip();
        }
    }

    private Integer readPositivePages() {
        while (true) {
            String value = readLine("Page count: ");
            if (value == null) {
                return null;
            }
            try {
                int pages = Integer.parseInt(value.strip());
                if (pages > 0) {
                    return pages;
                }
                output.accept("Page count must be greater than zero.");
            } catch (NumberFormatException e) {
                output.accept("Invalid page count. Please enter a whole number, such as 350.");
            }
        }
    }

    private String readLine(String prompt) {
        output.accept(prompt);
        String value = input.get();
        if (value == null) {
            output.accept("Input ended. Exiting.");
        }
        return value;
    }

    private void displayBooks() {
        var books = library.listBooks();
        if (books.isEmpty()) {
            output.accept("Your library is empty.");
            return;
        }
        for (Book book : books) {
            output.accept(book.getTitle() + " — " + book.getAuthor()
                + " | " + book.getGenre() + " | " + book.getPages() + " pages");
        }
    }
}
