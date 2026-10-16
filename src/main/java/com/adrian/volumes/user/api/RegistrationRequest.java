package com.adrian.volumes.user.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationRequest(String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
