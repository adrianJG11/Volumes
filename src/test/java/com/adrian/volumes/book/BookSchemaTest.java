package com.adrian.volumes.book;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookSchemaTest {
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @Test
    void migrationPreservesRowsWhenRunAgainAndEnforcesConstraints() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insert(first, "The Brothers Karamazov", 800);
        insert(second, "The Brothers Karamazov", 800);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(java.util.List.of(first, second), jdbc.sql(
            "SELECT id FROM books ORDER BY insertion_order").query(UUID.class).list());
        assertThrows(DataIntegrityViolationException.class, () -> insert(first, "Duplicate ID", 100));
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), "Invalid pages", 0));
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), "   ", 100));
        assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), null, 100));
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM books").query(Long.class).single());
    }

    private void insert(UUID id, String title, int pages) {
        jdbc.sql("""
            INSERT INTO books (id, title, author, genre, pages)
            VALUES (:id, :title, 'Fyodor Dostoevsky', 'Fiction', :pages)
            """).param("id", id).param("title", title).param("pages", pages).update();
    }
}
