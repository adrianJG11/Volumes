CREATE TABLE books (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0),
    author TEXT NOT NULL CHECK (length(btrim(author)) > 0),
    genre TEXT NOT NULL CHECK (length(btrim(genre)) > 0),
    pages INTEGER NOT NULL CHECK (pages > 0),
    insertion_order BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE
);
