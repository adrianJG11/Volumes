package com.adrian.volumes.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
