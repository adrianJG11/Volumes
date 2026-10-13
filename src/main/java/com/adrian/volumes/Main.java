package com.adrian.volumes;

import com.adrian.volumes.cli.LibraryConsole;
import com.adrian.volumes.library.Library;

public class Main {
    public static void main(String[] args) {
        Library library = new Library();

        LibraryConsole console = new LibraryConsole(library, IO::readln, IO::println);
        console.run();
    }
}
