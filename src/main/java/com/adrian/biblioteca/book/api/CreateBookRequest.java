package com.adrian.biblioteca.book.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBookRequest(
    @NotBlank String title,
    @NotBlank String author,
    @NotBlank String genre,
    @NotNull @Positive Integer pages
) {}
