CREATE TABLE library_entries (
    user_id UUID NOT NULL REFERENCES users(id),
    book_id UUID NOT NULL REFERENCES books(id),
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'READING', 'FINISHED')),
    addition_order BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    PRIMARY KEY (user_id, book_id)
);
