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
            var repository = new BookRepository(jdbc);
            var entry = repository.findAll().getFirst();
            assertNull(entry.edition());
            var edition = new BookEdition("9780140449242", "OL3701480M", 8457009L);
            repository.setEdition(entry.id(), edition);
            assertEquals(edition, repository.findById(entry.id()).orElseThrow().edition());
            for (String assignment : new String[]{"isbn = NULL", "open_library_id = NULL", "cover_id = -1", "isbn = 'abc'", "open_library_id = '/works/OL1W'"})
                assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE books SET " + assignment).update());
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(edition, repository.findById(entry.id()).orElseThrow().edition());
            repository.setEdition(entry.id(), null);
            assertNull(repository.findById(entry.id()).orElseThrow().edition());
        }
    }
}
