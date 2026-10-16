package com.adrian.volumes.user.api;

import com.adrian.volumes.user.User;
import com.adrian.volumes.user.UserRegistration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class RegistrationController {
    private final UserRegistration registration;

    public RegistrationController(UserRegistration registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public User register(@RequestBody RegistrationRequest request) {
        return registration.register(request.email(), request.password());
    }
}
