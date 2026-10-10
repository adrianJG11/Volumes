# Personal Library

A learning project built with Java. The current console application creates two books and prints their titles and authors.

## Requirements

- JDK 25
- Maven
- Development environment: Ubuntu in WSL, with VS Code

## Build and run

```bash
mvn compile && java -cp target/classes com.adrian.biblioteca.Main
```

## Structure

- `pom.xml`: Maven project and compiler configuration.
- `src/main/java/com/adrian/biblioteca/Main.java`: application entry point.
- `src/main/java/com/adrian/biblioteca/Book.java`: book fields and constructor.
- `target/`: generated build output, excluded from Git.

Page counts in the sample data are illustrative and vary by edition.

## Next session

Introduce Git basics and implement a Library collection with operations to add and list books.

## Planned direction

Java and Spring Boot will manage the API, PostgreSQL access, accounts, shared catalog, personal libraries, reading statuses, and ratings. A separate Python service will later provide recommendations and a conversational agent through an API. The frontend comes later.

MyVault remains a separate project.
