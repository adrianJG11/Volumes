package com.adrian.volumes.book;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EditionMigrationTest {
    @Test
    void upgradesExistingBooksWithoutInventingEditions() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:tc:postgresql:18:///edition_migration", "test", "test")) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("4").load().migrate();
            var jdbc = JdbcClient.create(source);
            jdbc.sql("INSERT INTO books (id, title, author, genre, pages) VALUES (:id, 'Book', 'Author', 'Fiction', 200)")
                .param("id", UUID.randomUUID()).update();
            var before = jdbc.sql("SELECT * FROM books").query().singleRow();
            var flyway = Flyway.configure().dataSource(source).target("5").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(before, jdbc.sql("SELECT id, title, author, genre, pages, insertion_order FROM books").query().singleRow());
            for (String assignment : new String[]{"isbn = NULL", "open_library_id = NULL", "cover_id = -1", "isbn = 'abc'", "open_library_id = '/works/OL1W'"}) {
                jdbc.sql("UPDATE books SET isbn = '9780140449242', open_library_id = 'OL3701480M', cover_id = 8457009").update();
                assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE books SET " + assignment).update());
            }
            assertEquals(0, flyway.migrate().migrationsExecuted);
        }
    }

    @Test
    void movesOpenLibraryCoverIdsToUrlsAndAllowsOnlyKnownCoverHosts() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:tc:postgresql:18:///cover_migration", "test", "test")) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = JdbcClient.create(source);
            UUID covered = UUID.randomUUID(), uncovered = UUID.randomUUID(), plain = UUID.randomUUID();
            jdbc.sql("""
                INSERT INTO books (id, title, author, genre, pages, isbn, open_library_id, cover_id) VALUES
                    (:covered, 'Covered', 'Author', 'Fiction', 200, '9780140449242', 'OL3701480M', 8457009),
                    (:uncovered, 'Uncovered', 'Author', 'Fiction', 200, '9780140449266', 'OL1M', NULL),
                    (:plain, 'Plain', 'Author', 'Fiction', 200, NULL, NULL, NULL)
                """).param("covered", covered).param("uncovered", uncovered).param("plain", plain).update();
            assertEquals(1, Flyway.configure().dataSource(source).target("6").load().migrate().migrationsExecuted);
            var repository = new BookRepository(jdbc);
            assertEquals(new BookEdition("9780140449242", "OL3701480M", "https://covers.openlibrary.org/b/id/8457009-M.jpg?default=false"),
                repository.findById(covered).orElseThrow().edition());
            assertEquals(new BookEdition("9780140449266", "OL1M", null), repository.findById(uncovered).orElseThrow().edition());
            assertNull(repository.findById(plain).orElseThrow().edition());

            // An ISBN unknown to Open Library is now valid, with a cover from another allowed host.
            var itunes = new BookEdition("9788439710349", null, "https://is1-ssl.mzstatic.com/image/thumb/a/600x600bb.jpg");
            repository.setEdition(plain, itunes);
            assertEquals(itunes, repository.findById(plain).orElseThrow().edition());
            assertNull(itunes.sourceUrl());
            for (String assignment : new String[]{"cover_url = 'http://covers.openlibrary.org/b/id/1-M.jpg'",
                    "cover_url = 'https://evil.example/cover.jpg'", "isbn = NULL", "open_library_id = '/works/OL1W'"})
                assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.sql("UPDATE books SET " + assignment + " WHERE id = :id").param("id", plain).update(), assignment);
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE books SET isbn = NULL, open_library_id = NULL, cover_url = 'https://books.google.com/x' WHERE id = :id")
                .param("id", plain).update());
            repository.setEdition(plain, null);
            assertNull(repository.findById(plain).orElseThrow().edition());
        }
    }
}
