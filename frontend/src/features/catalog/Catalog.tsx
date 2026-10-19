import { useEffect, useState } from 'react'
import { AddToLibrary } from '../library/AddToLibrary'
import { AddBook } from './AddBook'
import { EditionEditor } from './EditionEditor'
import { BookCover } from './BookCover'
import { getBooks, type Book } from './api'

const pageCount = new Intl.NumberFormat('en')

type CatalogState =
  | { status: 'loading' }
  | { status: 'success'; books: Book[] }
  | { status: 'error' }

export function Catalog({ accountId, onSessionChanged }: { accountId?: string; onSessionChanged: () => void }) {
  const [state, setState] = useState<CatalogState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getBooks(controller.signal).then(
      (books) => {
        if (!controller.signal.aborted) setState({ status: 'success', books })
      },
      () => {
        if (!controller.signal.aborted) setState({ status: 'error' })
      },
    )
    return () => controller.abort()
  }, [attempt])

  function updateBook(book: Book) {
    setState((previous) => previous.status === 'success'
      ? { status: 'success', books: previous.books.map((item) => item.id === book.id ? book : item) }
      : previous)
  }

  function addBook(book: Book) {
    setState((previous) => previous.status === 'success'
      ? { status: 'success', books: [...previous.books, book] }
      : previous)
  }

  function reload() {
    setState({ status: 'loading' })
    setAttempt((previous) => previous + 1)
  }

  return (
    <section aria-labelledby="catalog-title" className="mt-10 sm:mt-12">
      <div className="flex flex-wrap items-baseline justify-between gap-3 border-b border-border pb-4">
        <h2 id="catalog-title" className="text-sm font-semibold">All books</h2>
        {state.status === 'success' && (
          <p className="text-sm text-muted tabular-nums">
            {state.books.length} {state.books.length === 1 ? 'book' : 'books'}
          </p>
        )}
      </div>
      {accountId && state.status === 'success' && <AddBook key={accountId} onAdded={addBook} onSessionChanged={onSessionChanged} />}
      {state.status === 'loading' && (
        <p role="status" className="py-10 text-muted">Loading books…</p>
      )}
      {state.status === 'error' && (
        <div className="py-10">
          <p role="alert" className="text-lg font-medium">We couldn’t load the catalog.</p>
          <p className="mt-2 text-muted">Check your connection and try again.</p>
          <button type="button" onClick={reload} className="mt-5 min-h-11 border-b-2 border-accent py-2 font-semibold text-accent hover:border-foreground hover:text-foreground">
            Try again
          </button>
        </div>
      )}
      {state.status === 'success' && (state.books.length === 0 ? (
        <div className="py-10">
          <p role="status" className="text-lg font-medium">No books in the catalog yet.</p>
          <p className="mt-2 text-muted">{accountId ? 'Add the first one with “Add a book” above.' : 'Check again later for new books.'}</p>
          <button type="button" onClick={reload} className="mt-5 min-h-11 border-b-2 border-accent py-2 font-semibold text-accent hover:border-foreground hover:text-foreground">
            Refresh catalog
          </button>
        </div>
      ) : (
        <ul className="divide-y divide-border">
          {state.books.map((book) => (
            <li key={book.id} className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-5 py-7 sm:gap-x-8 sm:py-8">
              <BookCover key={book.edition?.coverUrl ?? 'missing'} url={book.edition?.coverUrl} />
              <div className="flex min-w-0 flex-col justify-between gap-5 py-1 sm:flex-row sm:gap-8">
                <div className="min-w-0">
                  <h3 className="max-w-xl font-serif text-2xl leading-snug text-pretty wrap-anywhere sm:text-3xl">{book.title}</h3>
                  <p className="mt-2 text-sm text-muted wrap-anywhere sm:text-base">{book.author}</p>
                  {book.edition?.sourceUrl && (
                    <a href={book.edition.sourceUrl} target="_blank" rel="noopener noreferrer"
                      aria-label={`View the edition of ${book.title} on Open Library (opens in a new tab)`}
                      className="mt-2 inline-flex min-h-11 items-center text-xs text-muted underline decoration-border underline-offset-4 hover:text-accent">
                      Edition on Open Library
                    </a>
                  )}
                </div>
                <dl className="flex shrink-0 flex-wrap gap-x-5 gap-y-2 text-sm text-muted sm:max-w-40 sm:flex-col sm:text-right">
                  <div className="max-w-40 wrap-anywhere"><dt className="sr-only">Genre</dt><dd>{book.genre}</dd></div>
                  <div><dt className="sr-only">Length</dt><dd className="tabular-nums">{pageCount.format(book.pages)} pages</dd></div>
                </dl>
              </div>
              {accountId && <AddToLibrary key={'library-' + accountId} bookId={book.id} title={book.title} onSessionChanged={onSessionChanged} />}
              {accountId && <EditionEditor key={accountId} book={book} onUpdated={updateBook} onSessionChanged={onSessionChanged} />}
            </li>
          ))}
        </ul>
      ))}
    </section>
  )
}
