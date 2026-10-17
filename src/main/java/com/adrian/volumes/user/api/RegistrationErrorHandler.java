package com.adrian.volumes.user.api;

import com.adrian.volumes.user.DuplicateEmailException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegistrationController.class)
@Order(-1)
public class RegistrationErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidAccount(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail duplicateAccount(DuplicateEmailException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
