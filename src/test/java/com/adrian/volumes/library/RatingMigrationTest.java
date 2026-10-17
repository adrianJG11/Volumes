package com.adrian.volumes.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RatingMigrationTest {
    @Test
    void upgradesExistingLibraryAndEnforcesOptionalRatingRange() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:tc:postgresql:18:///ratings_migration", "test", "test")) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("3").load().migrate();
            var jdbc = JdbcClient.create(source);
            UUID userId = UUID.randomUUID();
            UUID bookId = UUID.randomUUID();
            jdbc.sql("""
                INSERT INTO users (id, email, password_hash)
                VALUES (:id, 'reader@example.com', 'test-hash-placeholder')
                """).param("id", userId).update();
            jdbc.sql("""
                INSERT INTO books (id, title, author, genre, pages)
                VALUES (:id, 'The Brothers Karamazov', 'Fyodor Dostoevsky', 'Fiction', 800)
                """).param("id", bookId).update();
            jdbc.sql("""
                INSERT INTO library_entries (user_id, book_id, status)
                VALUES (:userId, :bookId, 'READING')
                """).param("userId", userId).param("bookId", bookId).update();
            var entryBefore = jdbc.sql("SELECT * FROM library_entries").query().singleRow();
            var bookBefore = jdbc.sql("SELECT * FROM books").query().singleRow();
            var userBefore = jdbc.sql("SELECT * FROM users").query().singleRow();

            var flyway = Flyway.configure().dataSource(source).target("4").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(entryBefore, jdbc.sql("""
                SELECT user_id, book_id, status, addition_order FROM library_entries
                """).query().singleRow());
            assertEquals(bookBefore, jdbc.sql("SELECT * FROM books").query().singleRow());
            assertEquals(userBefore, jdbc.sql("SELECT * FROM users").query().singleRow());
            assertNull(jdbc.sql("SELECT rating FROM library_entries").query().singleRow().get("rating"));

            for (int rating = 1; rating <= 5; rating++) {
                setRating(jdbc, rating);
                assertEquals(rating, jdbc.sql("SELECT rating FROM library_entries").query(Integer.class).single());
            }
            for (int rating : new int[] {-1, 0, 6}) {
                assertThrows(DataIntegrityViolationException.class, () -> setRating(jdbc, rating));
                assertEquals(5, jdbc.sql("SELECT rating FROM library_entries").query(Integer.class).single());
            }
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(5, jdbc.sql("SELECT rating FROM library_entries").query(Integer.class).single());
            jdbc.sql("UPDATE library_entries SET rating = NULL").update();
            assertNull(jdbc.sql("SELECT rating FROM library_entries").query().singleRow().get("rating"));
            assertEquals(entryBefore, jdbc.sql("""
                SELECT user_id, book_id, status, addition_order FROM library_entries
                """).query().singleRow());
        }
    }

    private void setRating(JdbcClient jdbc, int rating) {
        // This isolated fixture contains exactly one library entry.
        jdbc.sql("UPDATE library_entries SET rating = :rating").param("rating", rating).update();
    }
}
