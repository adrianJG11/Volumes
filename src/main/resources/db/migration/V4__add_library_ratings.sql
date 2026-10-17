ALTER TABLE library_entries
    ADD COLUMN rating SMALLINT,
    ADD CONSTRAINT library_entries_rating_range CHECK (rating BETWEEN 1 AND 5);
