-- Covers can now come from Open Library, Google Books or iTunes, and Open Library may not know an ISBN.
ALTER TABLE books ADD COLUMN cover_url TEXT;
UPDATE books SET cover_url = 'https://covers.openlibrary.org/b/id/' || cover_id || '-M.jpg?default=false'
    WHERE cover_id IS NOT NULL;
ALTER TABLE books DROP CONSTRAINT valid_edition;
ALTER TABLE books DROP COLUMN cover_id;
ALTER TABLE books ADD CONSTRAINT valid_edition CHECK (
    (isbn IS NULL AND open_library_id IS NULL AND cover_url IS NULL)
    OR (isbn IS NOT NULL AND isbn ~ '^97[89][0-9]{10}$'
        AND (open_library_id IS NULL OR open_library_id ~ '^OL[0-9]+M$')
        AND (cover_url IS NULL OR (length(cover_url) <= 1000 AND cover_url ~
            '^https://(covers\.openlibrary\.org|books\.google\.com|books\.googleusercontent\.com|is[0-9]+-ssl\.mzstatic\.com)/')))
);
