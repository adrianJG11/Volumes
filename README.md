# Volumes

A personal book catalog and reading library built with Java 25 and Spring Boot. Includes a REST API and a console application; both currently store data in memory.

## Requirements

- JDK 25
- Maven

## API

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

POST returns `201 Created`, a UUID, and a `Location` header. Use the returned ID to retrieve or update the book:

```bash
curl -i http://localhost:8080/books/BOOK_ID
curl -i -X PUT http://localhost:8080/books/BOOK_ID \
  -H 'Content-Type: application/json' \
  -d '{"title":"Crime and Punishment","author":"Fyodor Dostoevsky","genre":"Classic fiction","pages":400}'
```

Replace `BOOK_ID` with the actual UUID. PUT requires all four fields and preserves the ID. Invalid requests return `400` with JSON error details; unknown IDs return `404`. Invalid updates leave the existing book unchanged. Duplicate metadata is allowed with distinct IDs.

Restarting the API clears the catalog. Authentication and PostgreSQL are planned; this is a local learning application.

## Console

```bash
mvn compile && java -cp target/classes com.adrian.volumes.Main
```

Choose add, list, or exit. The console has its own in-memory library, separate from the API.

## Tests and packaging

```bash
mvn verify
java -jar target/volumes-0.1.0-SNAPSHOT.jar
```

`mvn test` runs the tests without packaging. Tests cover domain rules, catalog concurrency and updates, console behavior, and real HTTP requests. CI also runs scripted console and packaged-API smoke checks.

## Structure

Under `src/main/java/com/adrian/volumes`:

- `VolumesApplication.java`: Spring Boot API entry point.
- `Main.java`: console entry point.
- `book/`: validated book data and in-memory catalog service.
- `book/api/`: HTTP controller, request/response records, and error handling.
- `library/`: console library collection with protected snapshots.
- `cli/`: menu, prompts, and output.

Tests mirror these packages under `src/test/java`. Generated files are in `target/`.
