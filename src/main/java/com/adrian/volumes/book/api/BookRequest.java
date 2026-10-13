package com.adrian.volumes.book.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookRequest(
    @NotBlank String title,
    @NotBlank String author,
    @NotBlank String genre,
    @NotNull @Positive Integer pages
) {}
