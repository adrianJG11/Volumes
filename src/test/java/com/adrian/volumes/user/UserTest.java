package com.adrian.volumes.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    @Test
    void normalizesEmailAndPreservesIdentity() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "  Reader@Example.com  ");
        assertEquals(id, user.id());
        assertEquals("reader@example.com", user.email());
        assertEquals(new User(id, "reader@example.com"), user);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "reader", "reader@", "@example.com", "reader@example", "a b@example.com", "a@@example.com"})
    void rejectsMissingOrMalformedEmail(String email) {
        assertThrows(IllegalArgumentException.class, () -> new User(UUID.randomUUID(), email));
    }

    @Test
    void rejectsMissingIdAndOversizedEmail() {
        assertThrows(IllegalArgumentException.class, () -> new User(null, "reader@example.com"));
        assertThrows(IllegalArgumentException.class,
            () -> new User(UUID.randomUUID(), "a".repeat(250) + "@example.com"));
    }
}
