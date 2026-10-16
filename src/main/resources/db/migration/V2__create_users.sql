CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL CHECK (length(btrim(password_hash)) > 0),
    CONSTRAINT users_email_normalized CHECK (
        email = lower(btrim(email)) AND length(email) > 0
    )
);
