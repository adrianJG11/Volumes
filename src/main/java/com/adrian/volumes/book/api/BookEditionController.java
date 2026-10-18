package com.adrian.volumes.book.api;

import com.adrian.volumes.book.BookEditions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/books/{id}/edition")
public class BookEditionController {
    public record EditionRequest(@NotBlank String isbn) {}
    private final BookEditions editions;

    public BookEditionController(BookEditions editions) { this.editions = editions; }

    @PutMapping
    public BookResponse associate(@PathVariable("id") UUID id, @Valid @RequestBody EditionRequest request) {
        return BookResponse.from(editions.associate(id, request.isbn()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("id") UUID id) { editions.remove(id); }
}
