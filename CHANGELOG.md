# Changelog

## 0.1.0 — 2026-11-05

First tagged release of the book-library MVP. Development is paused at this
functional milestone; ML/DL is deferred.

### Application

- Java 25 / Spring Boot API and React frontend.
- Registration, session login/logout and CSRF-protected writes.
- Shared catalog, Open Library title search, ISBN edition metadata and covers.
- Per-user reading lists, reading statuses and ratings.
- PostgreSQL persistence, Flyway migrations and Docker Compose deployment.
- Independent in-memory Java console.

### Saved experimental work

- Offline Python corpus audit and TF-IDF baseline, with seven behavior tests.
- Eight-book seed list, development queries and documented pilot findings.
- No integration into the application and no validated recommendation-quality claims.
- Downloaded corpus/cache files remain local and ignored.

### Deferred

Corpus expansion, independent relevance review, embeddings, personalization,
ML/DL models, chatbot and application integration. See issue #21 and the
recommender README before resuming.
