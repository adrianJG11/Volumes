package com.adrian.volumes.user.api;

import com.adrian.volumes.user.User;
import com.adrian.volumes.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class SessionController {
    private final UserRepository users;

    public SessionController(UserRepository users) {
        this.users = users;
    }

    public record CsrfResponse(String headerName, String token) {}

    @GetMapping("/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getHeaderName(), csrf.getToken());
    }

    @GetMapping("/auth/me")
    public User currentUser(Authentication authentication) {
        return users.findById(UUID.fromString(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
    }
}
