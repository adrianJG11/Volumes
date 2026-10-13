package com.adrian.biblioteca;

import com.adrian.biblioteca.cli.LibraryConsole;
import com.adrian.biblioteca.library.Library;

public class Main {
    public static void main(String[] args) {
        Library library = new Library();

        LibraryConsole console = new LibraryConsole(library, IO::readln, IO::println);
        console.run();
    }
}
