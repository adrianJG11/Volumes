package com.adrian.volumes.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class UserRegistration {
    private final UserRepository repository;
    private final PasswordEncoder passwords;

    public UserRegistration(UserRepository repository, PasswordEncoder passwords) {
        this.repository = repository;
        this.passwords = passwords;
    }

    public User register(String email, String password) {
        User user = new User(UUID.randomUUID(), email);
        // Preserve the exact password, including spaces. BCrypt has a 72-byte limit.
        if (password == null || password.isBlank()
                || password.codePointCount(0, password.length()) < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must contain at least 15 characters and at most 72 UTF-8 bytes.");
        }
        String hash = passwords.encode(password);
        return repository.insert(user, hash).orElseThrow(DuplicateEmailException::new);
    }
}
