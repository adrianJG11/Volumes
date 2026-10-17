package com.adrian.volumes.library.api;

import com.adrian.volumes.book.BookCatalog;
import com.adrian.volumes.library.LibraryEntry;
import com.adrian.volumes.library.PersonalLibraryRepository;
import com.adrian.volumes.library.ReadingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/me/library")
public class PersonalLibraryController {
    private final PersonalLibraryRepository library;
    private final BookCatalog catalog;

    public PersonalLibraryController(PersonalLibraryRepository library, BookCatalog catalog) {
        this.library = library;
        this.catalog = catalog;
    }

    public record AddRequest(@NotNull UUID bookId) {}
    public record StatusRequest(@NotNull ReadingStatus status) {}
    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer rating) {}

    private UUID owner(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    @GetMapping
    public List<LibraryEntry> list(Authentication authentication) {
        return library.list(owner(authentication));
    }

    @PostMapping
    public ResponseEntity<LibraryEntry> add(Authentication authentication, @Valid @RequestBody AddRequest request) {
        if (catalog.findById(request.bookId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found.");
        }
        var entry = library.add(owner(authentication), request.bookId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Book is already in your library."));
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @PatchMapping("/{bookId}")
    public LibraryEntry update(Authentication authentication, @PathVariable("bookId") UUID bookId,
                               @Valid @RequestBody StatusRequest request) {
        return library.updateStatus(owner(authentication), bookId, request.status())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library entry not found."));
    }

    @PutMapping("/{bookId}/rating")
    public LibraryEntry rate(Authentication authentication, @PathVariable("bookId") UUID bookId,
                             @Valid @RequestBody RatingRequest request) {
        return library.updateRating(owner(authentication), bookId, request.rating())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library entry not found."));
    }

    @DeleteMapping("/{bookId}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearRating(Authentication authentication, @PathVariable("bookId") UUID bookId) {
        if (!library.clearRating(owner(authentication), bookId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Library entry not found.");
        }
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable("bookId") UUID bookId) {
        if (!library.remove(owner(authentication), bookId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Library entry not found.");
        }
    }
}
