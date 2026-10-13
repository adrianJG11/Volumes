# Personal Library

A Java console application to add and list books in memory. Choose add, list, or exit from the menu. Books are lost when the application closes.

## Requirements

- JDK 25
- Maven

## Run

```bash
mvn compile && java -cp target/classes com.adrian.biblioteca.Main
```

## Tests

```bash
mvn test
mvn verify
```

Tests cover book validation, library snapshots, and console interactions. GitHub Actions runs Maven verification and a scripted console smoke check.

## Structure

Under `src/main/java/com/adrian/biblioteca`:

- `Main.java`: constructs the application and connects console input/output.
- `book/Book.java`: book data, validation, and whitespace normalization.
- `library/Library.java`: in-memory collection with protected snapshots.
- `cli/LibraryConsole.java`: menu, prompts, retries, and display.

Tests mirror these packages under `src/test/java`. Generated output goes in `target/`.
