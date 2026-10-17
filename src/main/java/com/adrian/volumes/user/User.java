package com.adrian.volumes.user;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Account identity only; credentials do not belong in this model. */
public record User(UUID id, String email) {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public User {
        if (id == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null.");
        }
        email = email.strip().toLowerCase(Locale.ROOT);
        // Basic syntax validation, not proof that the mailbox exists.
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Email must have a valid address format and at most 254 characters.");
        }
    }
}
