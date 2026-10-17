package com.adrian.volumes.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RatingMigrationTest {
    @Test
    void upgradePreservesLibrariesAndAllowsOnlyRatingsOneToFive() throws Exception {
        // An isolated database starts at V3, like a checkout from the personal-library PR.
        try (var connection = DriverManager.getConnection(
                "jdbc:tc:postgresql:18:///ratings_migration", "test", "test")) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("3").load().migrate();
            var jdbc = JdbcClient.create(source);
            UUID userId = UUID.randomUUID();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            jdbc.sql("INSERT INTO users (id, email, password_hash) VALUES (:id, 'reader@example.com', 'test-hash-placeholder')")
                .param("id", userId).update();
            for (UUID bookId : List.of(first, second)) {
                jdbc.sql("INSERT INTO books (id, title, author, genre, pages) VALUES (:id, 'Title', 'Author', 'Fiction', 100)")
                    .param("id", bookId).update();
            }
            jdbc.sql("INSERT INTO library_entries (user_id, book_id, status) VALUES (:user, :book, 'FINISHED')")
                .param("user", userId).param("book", first).update();
            jdbc.sql("INSERT INTO library_entries (user_id, book_id) VALUES (:user, :book)")
                .param("user", userId).param("book", second).update();

            assertEquals(1, Flyway.configure().dataSource(source).load().migrate().migrationsExecuted);
            assertEquals("reader@example.com", jdbc.sql("SELECT email FROM users").query(String.class).single());
            assertEquals(2L, jdbc.sql("SELECT count(*) FROM books").query(Long.class).single());
            var rows = jdbc.sql("SELECT book_id, status, rating FROM library_entries ORDER BY addition_order")
                .query((row, number) -> List.of(row.getObject("book_id", UUID.class), row.getString("status"),
                    String.valueOf(row.getObject("rating", Integer.class)))).list();
            assertEquals(List.of(List.of(first, "FINISHED", "null"), List.of(second, "PENDING", "null")), rows);

            for (int valid : new int[] {1, 5}) {
                assertEquals(2, rate(jdbc, valid));
            }
            for (int invalid : new int[] {0, 6, -1}) {
                assertThrows(DataIntegrityViolationException.class, () -> rate(jdbc, invalid));
            }
            assertEquals(List.of(5, 5), jdbc.sql("SELECT rating FROM library_entries").query(Integer.class).list());
            assertEquals(2, jdbc.sql("UPDATE library_entries SET rating = NULL").update());
        }
    }

    private int rate(JdbcClient jdbc, int rating) {
        return jdbc.sql("UPDATE library_entries SET rating = :rating").param("rating", rating).update();
    }
}
