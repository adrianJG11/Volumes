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
                row.getString("genre"), row.getInt("pages")));

    private final JdbcClient jdbc;

    public BookRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public BookCatalog.Entry insert(UUID id, Book book) {
        return bind(jdbc.sql("""
            INSERT INTO books (id, title, author, genre, pages)
            VALUES (:id, :title, :author, :genre, :pages)
            RETURNING id, title, author, genre, pages
            """), id, book).query(BOOK_ROW).single();
    }

    public List<BookCatalog.Entry> findAll() {
        return List.copyOf(jdbc.sql("""
            SELECT id, title, author, genre, pages
            FROM books ORDER BY insertion_order
            """).query(BOOK_ROW).list());
    }

    public Optional<BookCatalog.Entry> findById(UUID id) {
        return jdbc.sql("""
            SELECT id, title, author, genre, pages FROM books WHERE id = :id
            """).param("id", id).query(BOOK_ROW).optional();
    }

    public Optional<BookCatalog.Entry> update(UUID id, Book book) {
        // One statement: an unknown ID changes no rows and returns an empty result.
        return bind(jdbc.sql("""
            UPDATE books SET title = :title, author = :author, genre = :genre, pages = :pages
            WHERE id = :id
            RETURNING id, title, author, genre, pages
            """), id, book).query(BOOK_ROW).optional();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, UUID id, Book book) {
        return statement.param("id", id)
            .param("title", book.getTitle()).param("author", book.getAuthor())
            .param("genre", book.getGenre()).param("pages", book.getPages());
    }
}
