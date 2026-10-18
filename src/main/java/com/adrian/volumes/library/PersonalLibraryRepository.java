package com.adrian.volumes.library;

import com.adrian.volumes.book.BookEdition;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PersonalLibraryRepository {
    private static final RowMapper<LibraryEntry> ENTRY = (row, number) -> new LibraryEntry(
        row.getObject("book_id", UUID.class), row.getString("title"), row.getString("author"),
        row.getString("genre"), row.getInt("pages"), ReadingStatus.valueOf(row.getString("status")),
        row.getObject("rating", Integer.class),
        row.getString("isbn") == null ? null : new BookEdition(row.getString("isbn"),
            row.getString("open_library_id"), row.getObject("cover_id", Long.class)));
    private final JdbcClient jdbc;

    public PersonalLibraryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<LibraryEntry> add(UUID userId, UUID bookId) {
        return jdbc.sql("""
            WITH added AS (
                INSERT INTO library_entries (user_id, book_id) VALUES (:userId, :bookId)
                ON CONFLICT (user_id, book_id) DO NOTHING
                RETURNING book_id, status, rating
            )
            SELECT a.book_id, b.title, b.author, b.genre, b.pages, b.isbn, b.open_library_id, b.cover_id, a.status, a.rating
            FROM added a JOIN books b ON b.id = a.book_id
            """).param("userId", userId).param("bookId", bookId).query(ENTRY).optional();
    }

    public List<LibraryEntry> list(UUID userId) {
        return jdbc.sql("""
            SELECT e.book_id, b.title, b.author, b.genre, b.pages, b.isbn, b.open_library_id, b.cover_id, e.status, e.rating
            FROM library_entries e JOIN books b ON b.id = e.book_id
            WHERE e.user_id = :userId ORDER BY e.addition_order
            """).param("userId", userId).query(ENTRY).list();
    }

    public Optional<LibraryEntry> updateStatus(UUID userId, UUID bookId, ReadingStatus status) {
        return jdbc.sql("""
            WITH updated AS (
                UPDATE library_entries SET status = :status
                WHERE user_id = :userId AND book_id = :bookId
                RETURNING book_id, status, rating
            )
            SELECT u.book_id, b.title, b.author, b.genre, b.pages, b.isbn, b.open_library_id, b.cover_id, u.status, u.rating
            FROM updated u JOIN books b ON b.id = u.book_id
            """).param("userId", userId).param("bookId", bookId).param("status", status.name())
            .query(ENTRY).optional();
    }

    public Optional<LibraryEntry> updateRating(UUID userId, UUID bookId, int rating) {
        return jdbc.sql("""
            WITH updated AS (
                UPDATE library_entries SET rating = :rating
                WHERE user_id = :userId AND book_id = :bookId
                RETURNING book_id, status, rating
            )
            SELECT u.book_id, b.title, b.author, b.genre, b.pages, b.isbn, b.open_library_id, b.cover_id, u.status, u.rating
            FROM updated u JOIN books b ON b.id = u.book_id
            """).param("userId", userId).param("bookId", bookId).param("rating", rating)
            .query(ENTRY).optional();
    }

    public boolean remove(UUID userId, UUID bookId) {
        return jdbc.sql("DELETE FROM library_entries WHERE user_id = :userId AND book_id = :bookId")
            .param("userId", userId).param("bookId", bookId).update() == 1;
    }

    public boolean clearRating(UUID userId, UUID bookId) {
        return jdbc.sql("UPDATE library_entries SET rating = NULL WHERE user_id = :userId AND book_id = :bookId")
            .param("userId", userId).param("bookId", bookId).update() == 1;
    }
}
