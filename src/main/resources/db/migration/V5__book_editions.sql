ALTER TABLE books
    ADD COLUMN isbn VARCHAR(13),
    ADD COLUMN open_library_id TEXT,
    ADD COLUMN cover_id BIGINT,
    ADD CONSTRAINT valid_edition CHECK (
        (isbn IS NULL AND open_library_id IS NULL AND cover_id IS NULL)
        OR (isbn IS NOT NULL AND isbn ~ '^97[89][0-9]{10}$'
            AND open_library_id IS NOT NULL AND open_library_id ~ '^OL[0-9]+M$'
            AND (cover_id IS NULL OR cover_id > 0))
    );
