"""Small, offline-first corpus audit and lexical baseline. Python standard library only."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import html
import json
import math
from pathlib import Path
import re
import sys
import time
import unicodedata
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
STOP = set("a an and are as at be by for from in is it of on or that the this to was with un una unos unas el la los las de del en y o que con por para como algo sobre quiero".split())


def tokens(text):
    text = ''.join(c for c in unicodedata.normalize('NFKD', text.casefold()) if not unicodedata.combining(c))
    return [word for word in re.findall(r'[^\W_]+', text) if len(word) > 1 and word not in STOP]


def clean_description(value):
    if isinstance(value, dict):
        value = value.get('value', '')
    if not isinstance(value, str):
        return ''
    return ' '.join(html.unescape(re.sub(r'<[^>]*>', ' ', value)).split())


def normalize(doc, work):
    key = doc.get('key', '')
    if not re.fullmatch(r'/works/OL\d+W', key) or work.get('key') != key:
        raise ValueError('Search/work identifiers do not match')
    title = work.get('title') or doc.get('title')
    if not isinstance(title, str) or not title.strip():
        raise ValueError('Missing title')
    synopsis = clean_description(work.get('description'))
    subjects = work.get('subjects', [])
    return dict(id=key, source='openlibrary', source_url='https://openlibrary.org'+key,
                title=title, authors=doc.get('author_name', []),
                edition_languages=doc.get('language', []),
                synopsis=synopsis, synopsis_language=None,
                synopsis_sha256=hashlib.sha256(synopsis.encode()).hexdigest(),
                subjects=[s for s in subjects if isinstance(s, str)] if isinstance(subjects, list) else [])


class OpenLibrary:
    def __init__(self, cache):
        self.cache = cache
        cache.mkdir(parents=True, exist_ok=True)
        self.last_request = 0.0

    def get(self, path):
        url = 'https://openlibrary.org' + path
        target = self.cache / (hashlib.sha256(url.encode()).hexdigest()+'.json')
        if target.exists():
            return json.loads(target.read_text())
        time.sleep(max(0, 1.1 - (time.monotonic()-self.last_request)))
        request = Request(url, headers={'User-Agent': 'Volumes-CorpusPilot/0.1 (https://github.com/adrianJG11/Volumes)', 'Accept': 'application/json'})
        self.last_request = time.monotonic()
        with urlopen(request, timeout=30) as response:
            data = json.load(response)
        # Failed responses are never cached. Reruns reuse successful requests.
        target.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
        return data


def ingest(seeds, client):
    if not isinstance(seeds, list) or not 1 <= len(seeds) <= 10:
        raise ValueError('Pilot accepts 1-10 curated books; use data dumps for larger corpora')
    books, failures, duplicates = {}, [], []
    for seed in seeds:
        try:
            query = urlencode(dict(title=seed['title'], author=seed['author'], limit=1,
                                   fields='key,title,author_name,language'))
            docs = client.get('/search.json?'+query).get('docs', [])
            if not docs:
                raise ValueError('No search result')
            doc = docs[0]
            key = doc.get('key', '')
            if not re.fullmatch(r'/works/OL\d+W', key):
                raise ValueError('Invalid work identifier')
            if key in books:
                duplicates.append(dict(seed=seed, id=key))
                continue
            book = normalize(doc, client.get(key+'.json'))
            book['requested'] = seed
            books[key] = book
        except (HTTPError, URLError, TimeoutError, OSError, ValueError, KeyError, TypeError) as error:
            failures.append(dict(seed=seed, error=str(error)))
            # Do not keep requesting after throttling or a service outage.
            if isinstance(error, HTTPError) and (error.code == 429 or error.code >= 500):
                break
    return list(books.values()), failures, duplicates


def audit(books):
    lengths = [len(tokens(b['synopsis'])) for b in books]
    return dict(books=len(books), with_synopsis=sum(n > 0 for n in lengths),
                with_at_least_40_content_words=sum(n >= 40 for n in lengths),
                with_subjects=sum(bool(b['subjects']) for b in books),
                note='Length is a triage signal, not a judgment of synopsis quality. Review text and source identity manually.',
                rows=[dict(id=b['id'], title=b['title'], content_words=n,
                           subjects=len(b['subjects']), source_url=b['source_url']) for b,n in zip(books,lengths)])


class LexicalIndex:
    """TF-IDF cosine similarity; no translation, stemming or model download."""
    def __init__(self, books):
        self.books = books
        counts = [Counter(tokens(' '.join([b['title'], b['synopsis'], *b['subjects']]))) for b in books]
        df = Counter(term for row in counts for term in row)
        self.idf = {term: math.log((1+len(books))/(1+n))+1 for term,n in df.items()}
        self.vectors = [self.vector(row) for row in counts]

    def vector(self, counts):
        vector = {term: (1+math.log(n))*self.idf[term] for term,n in counts.items() if term in self.idf}
        norm = math.sqrt(sum(v*v for v in vector.values()))
        return {term: v/norm for term,v in vector.items()} if norm else {}

    def search(self, query, k=10):
        if k < 1:
            raise ValueError('k must be positive')
        vector = self.vector(Counter(tokens(query)))
        results = []
        for book, document in zip(self.books, self.vectors):
            matched = sorted(vector.keys() & document.keys())
            score = sum(vector[t]*document[t] for t in matched)
            if score > 0:
                results.append(dict(id=book['id'], title=book['title'], score=score, matched_terms=matched))
        return sorted(results, key=lambda r: (-r['score'], r['id']))[:k]


def metrics(ranked, grades, k):
    relevant = {key for key, grade in grades.items() if grade > 0}
    if not relevant:
        raise ValueError('Each query needs at least one relevant judgment')
    gains = [grades.get(key, 0) for key in ranked[:k]]
    def dcg(values):
        return sum((2**grade-1)/math.log2(i+2) for i,grade in enumerate(values))
    ideal = dcg(sorted(grades.values(), reverse=True)[:k])
    return dict(recall=sum(key in relevant for key in ranked[:k])/len(relevant), ndcg=dcg(gains)/ideal)


def evaluate(books, queries, k):
    if not queries or k < 1:
        raise ValueError('Provide queries and a positive k')
    index = LexicalIndex(books)
    ids = {b['id'] for b in books}
    rows = []
    for query in queries:
        grades = query['relevance']
        if not grades or not set(grades) <= ids:
            raise ValueError('Judgments must reference books in this corpus')
        if any(type(g) is not int or not 0 <= g <= 3 for g in grades.values()):
            raise ValueError('Relevance grades must be integers from 0 to 3')
        hits = index.search(query['text'], k)
        rows.append(dict(query=query['text'], hits=hits,
                         **metrics([h['id'] for h in hits], grades, k)))
    return dict(baseline='tfidf-v1', k=k, queries=len(rows),
                mean_recall=sum(r['recall'] for r in rows)/len(rows),
                mean_ndcg=sum(r['ndcg'] for r in rows)/len(rows), rows=rows)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', type=Path, default=ROOT/'.data')
    sub = parser.add_subparsers(dest='command', required=True)
    fetch = sub.add_parser('ingest')
    fetch.add_argument('--seeds', type=Path, default=ROOT/'seeds.json')
    sub.add_parser('audit')
    search = sub.add_parser('search')
    search.add_argument('query')
    search.add_argument('--k', type=int, default=10)
    evaluation = sub.add_parser('evaluate')
    evaluation.add_argument('--queries', type=Path, required=True)
    evaluation.add_argument('--k', type=int, default=10)
    args = parser.parse_args()
    corpus = args.data/'corpus.json'
    if args.command == 'ingest':
        seeds = read(args.seeds)
        books, failures, duplicates = ingest(seeds, OpenLibrary(args.data/'cache'))
        report = dict(created_at=datetime.now(timezone.utc).isoformat(), requested=len(seeds),
                      failures=failures, duplicates=duplicates, **audit(books))
        save(args.data/'audit.json', report)
        # Preserve a previously usable snapshot if this fetch was incomplete.
        if not failures:
            save(corpus, books)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 1 if failures else 0
    books = read(corpus)
    if args.command == 'audit':
        result = audit(books)
    elif args.command == 'search':
        result = LexicalIndex(books).search(args.query, args.k)
    else:
        result = evaluate(books, read(args.queries), args.k)
        result['corpus_sha256'] = hashlib.sha256(corpus.read_bytes()).hexdigest()
        result['queries_sha256'] = hashlib.sha256(args.queries.read_bytes()).hexdigest()
        save(args.data/'evaluation.json', result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Error: {error}', file=sys.stderr)
        sys.exit(1)
