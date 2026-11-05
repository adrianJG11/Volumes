# Pilot review — 2026-10-20

Local Open Library snapshot: eight requested works, eight fetched, no HTTP failures,
no repeated work IDs. Seven descriptions (87.5%); six sets of subjects (75%).
All seven descriptions were inspected and are in English. Edition language lists are
not evidence of synopsis language. This sample is selected, small and not representative.

| Work | Observation |
| --- | --- |
| Dune | Plot and themes are usable; also contains promotional text. |
| Nineteen Eighty-Four | Useful themes; publication history and collection links add noise. |
| Crime and Punishment | Missing description/subjects. Search picked OL32525579W, with translator Michael R. Katz among authors; review work identity before expansion. |
| The Count of Monte Cristo | Useful short plot summary; no subjects on this selected record. |
| Pride and Prejudice | Useful plot/social context. |
| Frankenstein | Some useful premise, much publication/author history. |
| The Hobbit | Useful adventure premise, plus promotional material. |
| And Then There Were None | Long description largely about publishing/title history and awards; length alone overstates its usefulness for plot recommendations. |

The library currently exposes edition IDs; this experiment uses work IDs. Resolving the
connection to Spring books remains a separate integration task.

## Development queries, not a benchmark

`eval/pilot-queries.json` contains four English/Spanish query pairs. Initial relevance
judgments were authored by the coding assistant after inspecting this snapshot, before
running these queries. Each has one strong match and seven zero labels. They demonstrate
retrieval and cross-language failure; they are not independent human validation or a
held-out test. They are intentionally specific to these descriptions and must not be
presented as broad recommendation quality. User review and more varied queries are next.

```sh
python3 recommender/baseline.py evaluate --queries recommender/eval/pilot-queries.json --k 3
```

The corpus and raw descriptions remain in ignored `.data/`. Repeated ingestion reuses
that cache. Search rankings may change on a fresh provider snapshot; evaluation includes
corpus and query hashes to detect that difference. Keep the local snapshot for comparison.

## Decision

Do not expand by repeatedly fetching individual works. Investigate a bounded extract
from the official data dumps, then review identities and distinguish plot descriptions
from editorial text. Only then scale toward 200-500 books and evaluate embeddings.

Current provider guidance: https://openlibrary.org/developers/api

## Observed development run

TF-IDF v1, k=3, eight queries: mean recall=0.75,
mean nDCG=0.75. The four English queries retrieve the intended work first;
two Spanish queries do so through literal vocabulary/subject overlap, and two return
no matches. This is not evidence of multilingual semantic understanding.

Corpus SHA-256: `f31d3e31a591ec7ec91fd1689b906660b3bbf6395a12b83275e125892431b682`

Queries SHA-256: `579ddf11842bb2c745a395d920b96d93c6d14ec85a91c8f349362b7e25772653`

Seven offline behavior tests pass. A repeat ingestion reused the successful cache.
No Java/frontend changes or production database writes were made.
