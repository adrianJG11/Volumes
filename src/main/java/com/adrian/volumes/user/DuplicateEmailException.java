package com.adrian.volumes.user;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("An account with this email already exists.");
    }
}
