-- NULL means "not rated"; zero is never a substitute for an absent rating.
ALTER TABLE library_entries
    ADD COLUMN rating INTEGER CHECK (rating BETWEEN 1 AND 5);
