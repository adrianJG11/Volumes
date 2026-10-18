import { useEffect, useState } from 'react'
import { getBooks, type Book } from './api'

type CatalogState =
  | { status: 'loading' }
  | { status: 'success'; books: Book[] }
  | { status: 'error' }

export function Catalog() {
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

  function reload() {
    setState({ status: 'loading' })
    setAttempt((previous) => previous + 1)
  }

  return (
    <section aria-labelledby="catalog-title" className="mt-12 border-t border-border pt-8">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 id="catalog-title" className="text-2xl font-semibold">Catalog</h2>
        {state.status === 'success' && (
          <p className="text-sm text-muted tabular-nums">
            {state.books.length} {state.books.length === 1 ? 'book' : 'books'}
          </p>
        )}
      </div>
      {state.status === 'loading' && (
        <p role="status" className="py-10 text-muted">Loading books…</p>
      )}
      {state.status === 'error' && (
        <div className="py-10">
          <p role="alert" className="text-lg font-medium">We couldn’t load the catalog.</p>
          <p className="mt-2 text-muted">Check your connection and try again.</p>
          <button type="button" onClick={reload} className="mt-5 min-h-11 rounded-md bg-accent px-5 py-2 font-medium text-white hover:bg-accent/90">
            Try again
          </button>
        </div>
      )}
      {state.status === 'success' && (state.books.length === 0 ? (
        <div className="py-10">
          <p role="status" className="text-lg font-medium">No books in the catalog yet.</p>
          <p className="mt-2 text-muted">Check again later for new books.</p>
          <button type="button" onClick={reload} className="mt-5 min-h-11 rounded-md border border-border px-5 py-2 font-medium hover:bg-slate-50">
            Refresh catalog
          </button>
        </div>
      ) : (
        <ul className="mt-5 divide-y divide-border">
          {state.books.map((book) => (
            <li key={book.id} className="grid gap-3 py-6 sm:grid-cols-[1fr_auto] sm:gap-8">
              <div className="min-w-0">
                <h3 className="font-serif text-2xl leading-snug wrap-anywhere">{book.title}</h3>
                <p className="mt-1 text-muted wrap-anywhere">{book.author}</p>
              </div>
              <dl className="flex flex-wrap items-start gap-x-5 gap-y-1 text-sm text-muted sm:text-right">
                <div className="max-w-64 wrap-anywhere"><dt className="sr-only">Genre</dt><dd>{book.genre}</dd></div>
                <div><dt className="sr-only">Length</dt><dd className="tabular-nums">{book.pages} pages</dd></div>
              </dl>
            </li>
          ))}
        </ul>
      ))}
    </section>
  )
}
