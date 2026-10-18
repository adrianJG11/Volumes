package com.adrian.volumes.book;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookRepository {
    private static final RowMapper<BookCatalog.Entry> BOOK_ROW = (row, rowNumber) ->
        new BookCatalog.Entry(row.getObject("id", UUID.class),
            new Book(row.getString("title"), row.getString("author"),
                row.getString("genre"), row.getInt("pages")),
            row.getString("isbn") == null ? null : new BookEdition(row.getString("isbn"),
                row.getString("open_library_id"), row.getString("cover_url")));

    private final JdbcClient jdbc;

    public BookRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public BookCatalog.Entry insert(UUID id, Book book) {
        return bind(jdbc.sql("""
            INSERT INTO books (id, title, author, genre, pages)
            VALUES (:id, :title, :author, :genre, :pages)
            RETURNING id, title, author, genre, pages, isbn, open_library_id, cover_url
            """), id, book).query(BOOK_ROW).single();
    }

    public List<BookCatalog.Entry> findAll() {
        return List.copyOf(jdbc.sql("""
            SELECT id, title, author, genre, pages, isbn, open_library_id, cover_url
            FROM books ORDER BY insertion_order
            """).query(BOOK_ROW).list());
    }

    public Optional<BookCatalog.Entry> findById(UUID id) {
        return jdbc.sql("""
            SELECT id, title, author, genre, pages, isbn, open_library_id, cover_url FROM books WHERE id = :id
            """).param("id", id).query(BOOK_ROW).optional();
    }

    public Optional<BookCatalog.Entry> update(UUID id, Book book) {
        // One statement: an unknown ID changes no rows and returns an empty result.
        return bind(jdbc.sql("""
            UPDATE books SET title = :title, author = :author, genre = :genre, pages = :pages
            WHERE id = :id
            RETURNING id, title, author, genre, pages, isbn, open_library_id, cover_url
            """), id, book).query(BOOK_ROW).optional();
    }

    public Optional<BookCatalog.Entry> setEdition(UUID id, BookEdition edition) {
        return jdbc.sql("""
            UPDATE books SET isbn = :isbn, open_library_id = :editionId, cover_url = :coverUrl
            WHERE id = :id
            RETURNING id, title, author, genre, pages, isbn, open_library_id, cover_url
            """).param("id", id)
            .param("isbn", edition == null ? null : edition.isbn(), java.sql.Types.VARCHAR)
            .param("editionId", edition == null ? null : edition.openLibraryId(), java.sql.Types.VARCHAR)
            .param("coverUrl", edition == null ? null : edition.coverUrl(), java.sql.Types.VARCHAR)
            .query(BOOK_ROW).optional();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, UUID id, Book book) {
        return statement.param("id", id)
            .param("title", book.getTitle()).param("author", book.getAuthor())
            .param("genre", book.getGenre()).param("pages", book.getPages());
    }
}
