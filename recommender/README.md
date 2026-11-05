# Recommender: corpus pilot and lexical baseline

**Status: paused as of 2026-11-05.** Preserved with Volumes v0.1.0 for later
ML/DL work. This experiment is not called by the application. Issue
[#21](https://github.com/adrianJG11/Volumes/issues/21) records the unfinished scope;
resume by reviewing the checklist below and reopening that issue.

This is the first, offline experiment for **books only**. It does not change Spring,
PostgreSQL, accounts or the UI. Python 3.10+; standard library only.
Run commands from the repository root:

```sh
python3 -m unittest discover -s recommender -v
python3 recommender/baseline.py ingest
python3 recommender/baseline.py audit
python3 recommender/baseline.py search "crime guilt punishment"
python3 recommender/baseline.py search "crimen culpa castigo"
```

## Corpus and provenance

`seeds.json` is a deliberately small, biased pilot of eight familiar books, not a
representative recommendation corpus. Ingest searches a title/author pair and fetches
its first work result. Inspect the requested title/author, returned identity and source
URL in `.data/corpus.json` before accepting it: a top search hit is not an identity guarantee.
Deduplication uses Open Library **work** IDs, not edition IDs or ISBNs. No mapping to
Spring Book IDs is created yet. Cross-provider deduplication remains future work.

Raw responses are cached in `.data/cache`; re-running the same requests works offline.
Network requests have a 30-second timeout and are spaced at least 1.1 seconds apart.
Errors are recorded; ingest exits nonzero and preserves the previous corpus when any
request fails. Successful requests remain cached for a later retry. HTTP 429/5xx stops
further requests. The audit file describes the latest attempt, not necessarily the
previous preserved corpus; `audit` recomputes coverage of the saved corpus.

The pilot accepts at most ten seeds. Open Library asks clients to cache results and
avoid hundreds of individual requests. Use its data dumps for corpus expansion rather
than raising this guard or running successive batches:
https://openlibrary.org/developers/api

Synopsis strings and `{ "value": "..." }` descriptions are normalized. Missing text
stays missing; no generated synopsis substitutes for evidence. The SHA-256 digest tracks
text changes. `edition_languages` describes available editions, **not** the synopsis
language. `synopsis_language` stays unknown until reviewed. Forty content words is only
a triage threshold: manually assess accuracy, usefulness, language and spoilers.
Provider descriptions can contain markup, links and editorial notes; cleaning here is
minimal, not a complete ingestion pipeline. Downloaded content remains local and ignored.

## What the baseline measures

TF-IDF weights words by frequency and how distinctive they are across the corpus;
cosine similarity compares those weighted word vectors. We index title, synopsis and
subjects. The baseline folds accents/case and removes a small explicit English/Spanish
stop list; it does not translate, stem words, infer themes or understand negation.
Only positive matches are returned, with matched terms. A score is not a probability.
Spanish queries against English descriptions will often fail: keep those failures as
comparison cases for the future multilingual embeddings.

## Evaluation

Create a local JSON query file after reading the corpus, for example:

```json
[
  {
    "text": "a story about guilt after a crime",
    "relevance": {"/works/REPLACE_WITH_REAL_ID": 3}
  }
]
```

Use real IDs from the saved corpus. Grade each book 0 (irrelevant), 1 (weak), 2 (good),
3 (strong), ideally judging all eight books for each query before examining rankings.
These are human judgments, not facts from the provider. Do not generate favorable labels
from the search results themselves. Unjudged books score zero; recall refers only to
known relevant labels and is incomplete if judgments are incomplete.

```sh
python3 recommender/baseline.py evaluate --queries recommender/.data/queries.json --k 5
```

The report includes per-query results, mean recall/nDCG and hashes of the exact corpus
and queries. Use k=3 or 5 for this tiny pilot: recall@10 on eight books is not informative
about ordering. For the larger experiment, prepare roughly 50 queries, reserve a held-out
subset, and freeze the corpus, labels and preprocessing before comparing embeddings.
The unit tests use synthetic data to validate mechanics; they do **not** measure real
recommendation quality. There is no real quality score until judgments are reviewed.

## Next acceptance criteria

- Review the pilot's identities, descriptions and language; record coverage and failures.
- Decide a permitted, reproducible source for a 200-500 book corpus with useful synopses.
- Review relevance judgments, including Spanish/English pairs and negative constraints.
- Compare multilingual embeddings with this fixed baseline before adding personalization.
- Add FastAPI and Spring integration after the offline search is useful and measured.

No GPU, Ollama, pgvector or cloud service is required for this step. Python remains in
this monorepo under `recommender/`; the existing Java layout is preserved.
