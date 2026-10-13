# Personal Library

A Java console application to add and list books in memory. Choose add, list, or exit from the menu. Books are lost when the application closes.

## Requirements

- JDK 25
- Maven

## Run the console

```bash
mvn compile && java -cp target/classes com.adrian.biblioteca.Main
```

## Run the API

```bash
mvn spring-boot:run
```

In another WSL terminal:

```bash
curl -i http://localhost:8080/books
curl -i http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"Crime and Punishment","author":"Fyodor Dostoevsky","genre":"Fiction","pages":350}'
```

POST returns `201 Created`, the created book, and a `Location` header. GET that location to retrieve the book. GET `/books` lists the catalog. Invalid input returns `400`; an unknown book ID returns `404`. Book updates are not implemented yet.

The API catalog is held in memory and is separate from the console library. Restarting either application discards its data.

## Tests

```bash
mvn test
mvn verify
```

Tests cover book validation, library snapshots, and console interactions. GitHub Actions runs Maven verification and a scripted console smoke check.

## Structure

Under `src/main/java/com/adrian/biblioteca`:

- `Main.java`: starts the console.
- `BibliotecaApplication.java`: starts the Spring Boot API.
- `book/BookCatalog.java`: manages catalog IDs and in-memory storage.
- `book/api/`: HTTP controller, request/response records, and validation errors.
- `book/Book.java`: book data, validation, and whitespace normalization.
- `library/Library.java`: in-memory collection with protected snapshots.
- `cli/LibraryConsole.java`: menu, prompts, retries, and display.

Tests mirror these packages under `src/test/java`. Generated output goes in `target/`.
