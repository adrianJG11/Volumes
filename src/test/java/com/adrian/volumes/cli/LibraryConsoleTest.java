package com.adrian.volumes.cli;

import com.adrian.volumes.library.Library;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class LibraryConsoleTest {
    private final Library library = new Library();
    private final List<String> output = new ArrayList<>();

    private void run(String... lines) {
        var input = List.of(lines).iterator();
        new LibraryConsole(library, () -> input.hasNext() ? input.next() : null, output::add).run();
    }

    @Test
    void addsTwoBooksListsFullDetailsInOrderAndExits() {
        run("1", "First", "Author One", "Fiction", "100",
            "1", "Second", "Author Two", "History", "200", "2", "0");
        assertEquals(List.of("First", "Second"), library.listBooks().stream().map(book -> book.getTitle()).toList());
        var listings = output.stream().filter(line -> line.contains(" | ")).toList();
        assertEquals(2, listings.size());
        assertTrue(listings.get(0).contains("First") && listings.get(0).contains("Author One")
            && listings.get(0).contains("Fiction") && listings.get(0).contains("100"));
        assertTrue(listings.get(1).contains("Second") && listings.get(1).contains("Author Two")
            && listings.get(1).contains("History") && listings.get(1).contains("200"));
        assertTrue(output.getLast().contains("Goodbye"));
    }

    @Test
    void reportsAnEmptyLibrary() {
        run("2", "0");
        assertTrue(output.stream().anyMatch(line -> line.contains("empty")));
        assertTrue(library.listBooks().isEmpty());
    }

    @Test
    void invalidMenuChoicesDoNotChangeLibrary() {
        run("wrong", "", "9", " 0 ");
        assertEquals(3, output.stream().filter(line -> line.contains("Invalid option")).count());
        assertTrue(library.listBooks().isEmpty());
        assertTrue(output.getLast().contains("Goodbye"));
    }

    @Test
    void retriesBlankFieldsAndPageCountsWithoutRepeatingCompletedFields() {
        run("1", "", "  First  ", " ", " Author ", "\t", " Fiction ",
            "abc", "0", "-10", " 350 ", "0");
        assertEquals(1, library.listBooks().size());
        var book = library.listBooks().getFirst();
        assertEquals("First", book.getTitle());
        assertEquals("Author", book.getAuthor());
        assertEquals("Fiction", book.getGenre());
        assertEquals(350, book.getPages());
        assertEquals(3, output.stream().filter(line -> line.contains("must not be blank")).count());
        assertEquals(2, output.stream().filter(line -> line.startsWith("Book title:")).count());
        assertEquals(2, output.stream().filter(line -> line.startsWith("Book author:")).count());
        assertEquals(2, output.stream().filter(line -> line.startsWith("Book genre:")).count());
        assertEquals(4, output.stream().filter(line -> line.startsWith("Page count:")).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "3.5", "2147483648"})
    void retriesUnparseablePageCounts(String invalid) {
        run("1", "Title", "Author", "Genre", invalid, "1", "0");
        assertEquals(1, library.listBooks().getFirst().getPages());
        assertTrue(output.stream().anyMatch(line -> line.contains("Invalid page count")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void eofAtEveryPromptExitsWithoutAddingAPartialBook(int availableLines) {
        String[] lines = {"1", "Title", "Author", "Genre"};
        run(java.util.Arrays.copyOf(lines, availableLines));
        assertTrue(library.listBooks().isEmpty());
        assertTrue(output.getLast().contains("Input ended"));
    }

    @Test
    void eofDuringSecondBookKeepsTheFirstBook() {
        run("1", "First", "Author", "Genre", "100", "1", "Incomplete");
        assertEquals(1, library.listBooks().size());
        assertEquals("First", library.listBooks().getFirst().getTitle());
        assertTrue(output.getLast().contains("Input ended"));
    }

    @Test
    void exitDoesNotConsumeFurtherInput() {
        run("0", "1", "Title", "Author", "Genre", "100");
        assertTrue(library.listBooks().isEmpty());
        assertTrue(output.getLast().contains("Goodbye"));
    }
}
