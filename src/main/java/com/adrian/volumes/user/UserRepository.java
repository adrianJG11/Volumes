package com.adrian.volumes.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT id, email FROM users WHERE id = :id").param("id", id)
            .query((row, number) -> new User(row.getObject("id", UUID.class), row.getString("email")))
            .optional();
    }

    public Optional<UserDetails> findLoginByEmail(String email) {
        return jdbc.sql("SELECT id, password_hash FROM users WHERE email = :email")
            .param("email", email.strip().toLowerCase(Locale.ROOT))
            .query((row, number) -> (UserDetails)
                org.springframework.security.core.userdetails.User.withUsername(row.getString("id"))
                    .password(row.getString("password_hash")).authorities("USER").build())
            .optional();
    }

    public Optional<User> insert(User user, String passwordHash) {
        return jdbc.sql("""
            INSERT INTO users (id, email, password_hash) VALUES (:id, :email, :hash)
            ON CONFLICT (email) DO NOTHING
            RETURNING id, email
            """).param("id", user.id()).param("email", user.email()).param("hash", passwordHash)
            .query((row, number) -> new User(row.getObject("id", UUID.class), row.getString("email")))
            .optional();
    }
}
