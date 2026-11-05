# Add a books-only corpus audit and lexical recommendation baseline

Historical draft, published as [issue #21](https://github.com/adrianJG11/Volumes/issues/21).
Work paused on 2026-11-05; unchecked criteria remain deferred.

Volumes currently retrieves edition metadata but has no measured content search.
Add an isolated, reproducible Python experiment before choosing embedding/LLM services.

Acceptance criteria:
- [x] Fetch a bounded pilot with caching and source/work identifiers.
- [x] Report missing descriptions/subjects and inspect description usefulness.
- [x] Search locally using TF-IDF with traceable matched terms.
- [x] Provide graded-query evaluation and corpus/query hashes.
- [x] Cover provider variants, deduplication and metric behavior with offline tests.
- [ ] Independently review relevance judgments before claiming quality metrics.

Out of scope: Book-to-Work migration, user personalization, FastAPI, embeddings,
LLMs, production database changes and public deployment.
