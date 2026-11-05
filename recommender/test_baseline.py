import unittest
import io
import json
from pathlib import Path
import tempfile
from unittest.mock import patch
import baseline
from baseline import LexicalIndex, audit, evaluate, ingest, metrics, normalize


def book(key, title, synopsis, subjects=None):
    return dict(id=key, title=title, synopsis=synopsis, subjects=subjects or [])


class BaselineTest(unittest.TestCase):
    def test_provider_description_variants_and_work_identity(self):
        doc = dict(key='/works/OL1W', title='Example', language=['eng', 'spa'])
        for description in ['A <b>story</b> &amp; more', {'value': 'A story & more'}]:
            result = normalize(doc, dict(key=doc['key'], description=description))
            self.assertEqual('A story & more', result['synopsis'])
            self.assertIsNone(result['synopsis_language'])
        self.assertEqual('', normalize(doc, dict(key=doc['key']))['synopsis'])
        with self.assertRaises(ValueError):
            normalize(doc, dict(key='/books/OL1M'))

    def test_deduplicate_work_and_report_missing_seed(self):
        class Client:
            def get(self, path):
                if 'Missing' in path:
                    return {'docs': []}
                if path.startswith('/search'):
                    return {'docs': [dict(key='/works/OL1W', title='One')]}
                return dict(key='/works/OL1W', title='One')
        seeds = [dict(title=t, author='Author') for t in ['One', 'Another edition', 'Missing']]
        books, failures, duplicates = ingest(seeds, Client())
        self.assertEqual((1, 1, 1), (len(books), len(failures), len(duplicates)))
        self.assertEqual(0, audit(books)['with_synopsis'])

    def test_search_orders_content_and_does_not_invent_zero_matches(self):
        books = [book('a', 'Space', 'desert planet politics'), book('b', 'Home', 'family marriage romance')]
        index = LexicalIndex(books)
        self.assertEqual('b', index.search('marriage romance')[0]['id'])
        self.assertEqual([], index.search('astronauta'))
        self.assertEqual([], index.search('the and'))
        self.assertEqual('a', index.search('PLANET')[0]['id'])
        self.assertEqual('a', LexicalIndex([book('a', 'Crimen', 'culpabilidad')]).search('crimen')[0]['id'])

    def test_cached_fetch_does_not_need_network(self):
        with tempfile.TemporaryDirectory() as folder:
            client = baseline.OpenLibrary(Path(folder))
            with patch('baseline.urlopen', return_value=io.StringIO('{"key":"/works/OL1W"}')) as network:
                self.assertEqual('/works/OL1W', client.get('/works/OL1W.json')['key'])
                network.assert_called_once()
            with patch('baseline.urlopen', side_effect=AssertionError('Network should not be used')):
                self.assertEqual('/works/OL1W', client.get('/works/OL1W.json')['key'])

    def test_failed_ingest_preserves_saved_corpus(self):
        with tempfile.TemporaryDirectory() as folder:
            corpus = Path(folder)/'corpus.json'
            corpus.write_text('[{"id":"previous"}]')
            with patch('sys.argv', ['baseline.py', '--data', folder, 'ingest']), \
                 patch('baseline.ingest', return_value=([], [{'error': 'timeout'}], [])), \
                 patch('sys.stdout', new_callable=io.StringIO):
                self.assertEqual(1, baseline.main())
            self.assertEqual([{'id': 'previous'}], json.loads(corpus.read_text()))
            self.assertEqual(1, len(json.loads((Path(folder)/'audit.json').read_text())['failures']))

    def test_metrics_reward_order_and_count_missed_relevant_books(self):
        grades = {'a': 3, 'b': 1, 'c': 0}
        perfect = metrics(['a', 'b'], grades, 2)
        self.assertEqual(1, perfect['recall'])
        self.assertEqual(1, perfect['ndcg'])
        self.assertLess(metrics(['b', 'a'], grades, 2)['ndcg'], 1)
        self.assertEqual(0.5, metrics(['c', 'a'], grades, 2)['recall'])
        self.assertEqual(0, metrics([], grades, 2)['ndcg'])

    def test_evaluation_rejects_invalid_or_out_of_corpus_judgments(self):
        books = [book('a', 'Space', 'planet')]
        for grades in [{'missing': 3}, {'a': -1}, {'a': True}, {'a': 0}, {}]:
            with self.assertRaises(ValueError):
                evaluate(books, [dict(text='planet', relevance=grades)], 5)
        result = evaluate(books, [dict(text='planet', relevance={'a': 3})], 5)
        self.assertEqual(1, result['mean_ndcg'])


if __name__ == '__main__':
    unittest.main()
