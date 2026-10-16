package com.adrian.volumes.user;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserMigrationTest {
    @Test
    void upgradesExistingCatalogAndEnforcesAccountConstraints() throws Exception {
        // An isolated database starts at V1, like a checkout from the previous PR.
        try (var connection = DriverManager.getConnection(
                "jdbc:tc:postgresql:18:///users_migration", "test", "test")) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("1").load().migrate();
            var jdbc = JdbcClient.create(source);
            UUID bookId = UUID.randomUUID();
            jdbc.sql("""
                INSERT INTO books (id, title, author, genre, pages)
                VALUES (:id, 'The Brothers Karamazov', 'Fyodor Dostoevsky', 'Fiction', 800)
                """).param("id", bookId).update();

            var flyway = Flyway.configure().dataSource(source).target("2").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(bookId, jdbc.sql("SELECT id FROM books").query(UUID.class).single());
            assertEquals("The Brothers Karamazov", jdbc.sql("SELECT title FROM books").query(String.class).single());
            assertEquals(0L, jdbc.sql("SELECT count(*) FROM users").query(Long.class).single());

            UUID userId = UUID.randomUUID();
            insert(jdbc, userId, "reader@example.com", "test-hash-placeholder");
            assertThrows(DataIntegrityViolationException.class,
                () -> insert(jdbc, UUID.randomUUID(), "reader@example.com", "test-hash-placeholder"));
            assertThrows(DataIntegrityViolationException.class,
                () -> insert(jdbc, userId, "another@example.com", "test-hash-placeholder"));
            for (String email : new String[] {null, "", "Reader@example.com", " reader@example.com "}) {
                assertThrows(DataIntegrityViolationException.class,
                    () -> insert(jdbc, UUID.randomUUID(), email, "test-hash-placeholder"));
            }
            for (String hash : new String[] {null, "", "   "}) {
                assertThrows(DataIntegrityViolationException.class,
                    () -> insert(jdbc, UUID.randomUUID(), "another@example.com", hash));
            }
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(1L, jdbc.sql("SELECT count(*) FROM users").query(Long.class).single());

        }
    }

    private void insert(JdbcClient jdbc, UUID id, String email, String hash) {
        jdbc.sql("INSERT INTO users (id, email, password_hash) VALUES (:id, :email, :hash)")
            .param("id", id).param("email", email).param("hash", hash).update();
    }
}
