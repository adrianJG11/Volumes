import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { createBook, saveEdition, searchBooks, type Book, type BookSuggestion } from './api'

type Props = { onAdded: (book: Book) => void; onSessionChanged: () => void }
type Search =
  | { status: 'idle' | 'searching' }
  | { status: 'done'; results: BookSuggestion[] }
  | { status: 'error'; message: string }

const pageCount = new Intl.NumberFormat('en')
const EMPTY = { title: '', author: '', genre: '', pages: '', isbn: '' }
const field = 'mt-2 min-h-11 w-full border border-border bg-background px-3 text-base'

function wait(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => { clearTimeout(timer); reject(signal.reason) }, { once: true })
  })
}

// Searches and ISBN lookups are limited to one per second on the server; wait once and retry instead of failing.
async function retryOnRateLimit<T>(action: () => Promise<T>, signal?: AbortSignal): Promise<T> {
  try {
    return await action()
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 429)) throw error
    await wait(1100, signal)
    return action()
  }
}

export function AddBook({ onAdded, onSessionChanged }: Props) {
  const [open, setOpen] = useState(false)
  const [fields, setFields] = useState(EMPTY)
  const [picked, setPicked] = useState(false)
  const [search, setSearch] = useState<Search>({ status: 'idle' })
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const trigger = useRef<HTMLButtonElement>(null)
  const query = fields.title.trim()
  const sessionChanged = useRef(onSessionChanged)
  useEffect(() => { sessionChanged.current = onSessionChanged })

  useEffect(() => {
    if (!open || picked || query.length < 3) return
    const controller = new AbortController()
    // Wait until typing pauses so each search reflects a whole title, not every keystroke.
    const timer = setTimeout(() => {
      setSearch({ status: 'searching' })
      retryOnRateLimit(() => searchBooks(query, controller.signal), controller.signal).then(
        (results) => { if (!controller.signal.aborted) setSearch({ status: 'done', results }) },
        (failure) => {
          if (controller.signal.aborted) return
          const status = failure instanceof ApiError ? failure.status : 0
          if (status === 401 || status === 403) sessionChanged.current()
          setSearch({ status: 'error', message: status === 502 || status === 503
            ? 'Book search is unavailable right now. You can still fill in the details yourself.'
            : 'Could not search right now. You can still fill in the details yourself.' })
        })
    }, 600)
    return () => { clearTimeout(timer); controller.abort() }
  }, [open, picked, query])

  function update(name: keyof typeof EMPTY, value: string) {
    setFields((previous) => ({ ...previous, [name]: value }))
    setError('')
    if (name === 'title') {
      setPicked(false)
      if (value.trim().length < 3) setSearch({ status: 'idle' })
    }
  }

  function pick(suggestion: BookSuggestion) {
    setPicked(true)
    setSearch({ status: 'idle' })
    setFields({
      title: suggestion.title, author: suggestion.author ?? '', genre: suggestion.genre ?? '',
      pages: suggestion.pages?.toString() ?? '', isbn: suggestion.isbn ?? '',
    })
    document.getElementById('new-book-author')?.focus()
  }

  function close() {
    setOpen(false)
    setFields(EMPTY)
    setPicked(false)
    setSearch({ status: 'idle' })
    setError('')
    trigger.current?.focus()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const pages = Number(fields.pages)
    if (!Number.isInteger(pages) || pages <= 0) {
      setError('Pages must be a positive whole number.')
      return
    }
    setPending(true)
    setError('')
    setNotice('')
    let book: Book
    try {
      book = await createBook({ title: fields.title.trim(), author: fields.author.trim(), genre: fields.genre.trim(), pages })
    } catch (failure) {
      const status = failure instanceof ApiError ? failure.status : 0
      if (status === 401 || status === 403) onSessionChanged()
      setError(status === 400 ? 'Fill in the title, author and genre, and use a positive whole number of pages.'
        : status === 401 || status === 403 ? 'Your session has changed. Sign in again if needed, then retry.'
        : 'Could not add the book. Check your connection and try again.')
      setPending(false)
      return
    }
    let message = `“${book.title}” was added to the catalog.`
    const isbn = fields.isbn.trim()
    if (isbn) {
      try {
        book = await retryOnRateLimit(() => saveEdition(book.id, isbn))
        if (!book.edition?.coverUrl) message += ' No cover was found for this edition.'
      } catch {
        message += ' The ISBN could not be attached; use “Add ISBN” on the book to try again.'
      }
    }
    onAdded(book)
    setNotice(message)
    setPending(false)
    close()
  }

  return (
    <div className="border-b border-border py-4 text-sm">
      <button ref={trigger} type="button" aria-expanded={open} aria-controls="add-book-form" disabled={pending}
        onClick={() => { if (open) close(); else { setOpen(true); setNotice('') } }}
        className="min-h-11 font-semibold text-accent hover:text-foreground disabled:opacity-50">
        {open ? 'Close add book' : 'Add a book'}
      </button>
      {notice && <p role="status" className="mt-2 max-w-lg leading-6 text-muted">{notice}</p>}
      {open && (
        <form id="add-book-form" onSubmit={submit} className="max-w-lg pt-4 pb-2">
          <h3 className="font-serif text-2xl">Add a book</h3>
          <p className="mt-2 leading-6 text-muted">Type a title and choose a match to fill in the rest. You can change any field before adding it.</p>
          <fieldset disabled={pending} className="mt-4 grid gap-4 disabled:opacity-60">
            <div>
              <label htmlFor="new-book-title" className="block font-semibold">Title</label>
              <input id="new-book-title" name="title" type="text" placeholder="Start typing a title…" required maxLength={300} autoComplete="off" autoFocus
                aria-describedby="new-book-matches" value={fields.title}
                onChange={(event) => update('title', event.target.value)} className={field} />
              <div id="new-book-matches" aria-live="polite">
                {search.status === 'searching' && <p className="mt-2 text-muted">Searching…</p>}
                {search.status === 'error' && <p className="mt-2 text-muted">{search.message}</p>}
                {search.status === 'done' && search.results.length === 0 && (
                  <p className="mt-2 text-muted">No matches found. Fill in the details yourself.</p>
                )}
              </div>
              {search.status === 'done' && search.results.length > 0 && (
                <ul aria-label="Matching books" className="mt-2 divide-y divide-border border border-border">
                  {search.results.map((result, index) => (
                    <li key={`${result.isbn ?? result.title}-${index}`}>
                      <button type="button" onClick={() => pick(result)}
                        className="flex min-h-11 w-full items-center gap-3 px-3 py-2 text-left hover:bg-cover focus-visible:bg-cover">
                        <span aria-hidden="true" className="h-12 w-8 shrink-0 bg-cover">
                          {result.coverUrl && <img src={result.coverUrl} alt="" width={32} height={48} loading="lazy" referrerPolicy="no-referrer" className="h-full w-full object-cover"
                            onError={(event) => { event.currentTarget.style.display = 'none' }} />}
                        </span>
                        <span className="min-w-0">
                          <span className="block font-semibold wrap-anywhere">{result.title}</span>
                          <span className="block text-muted wrap-anywhere">
                            {[result.author ?? 'Unknown author', result.firstPublishYear, result.pages && `${pageCount.format(result.pages)} pages`]
                              .filter(Boolean).join(', ')}
                          </span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <label htmlFor="new-book-author" className="block font-semibold">Author</label>
              <input id="new-book-author" name="author" type="text" autoComplete="off" required maxLength={300} value={fields.author}
                onChange={(event) => update('author', event.target.value)} className={field} />
            </div>
            <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
              <div>
                <label htmlFor="new-book-genre" className="block font-semibold">Genre</label>
                <input id="new-book-genre" name="genre" type="text" autoComplete="off" required maxLength={100} value={fields.genre}
                  onChange={(event) => update('genre', event.target.value)} className={field} />
              </div>
              <div>
                <label htmlFor="new-book-pages" className="block font-semibold">Pages</label>
                <input id="new-book-pages" name="pages" type="number" autoComplete="off" required min={1} step={1} inputMode="numeric" value={fields.pages}
                  onChange={(event) => update('pages', event.target.value)} className={`${field} tabular-nums`} />
              </div>
            </div>
            <div>
              <label htmlFor="new-book-isbn" className="block font-semibold">ISBN <span className="font-normal text-muted">(optional, adds the cover)</span></label>
              <input id="new-book-isbn" name="isbn" type="text" maxLength={32} autoComplete="off" spellCheck={false} value={fields.isbn}
                onChange={(event) => update('isbn', event.target.value)} className={field} />
            </div>
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
              <button type="submit" className="min-h-11 border-b-2 border-accent font-semibold text-accent hover:border-foreground hover:text-foreground">
                {pending ? 'Adding…' : 'Add to catalog'}
              </button>
              <button type="button" onClick={close} className="min-h-11 text-muted hover:text-foreground">Cancel</button>
            </div>
          </fieldset>
          {error && <p role="alert" className="mt-3 leading-6 text-accent">{error}</p>}
        </form>
      )}
    </div>
  )
}
