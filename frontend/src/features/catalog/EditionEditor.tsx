import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { removeEdition, saveEdition, type Book } from './api'

type Props = { book: Book; onUpdated: (book: Book) => void; onSessionChanged: () => void }

export function EditionEditor({ book, onUpdated, onSessionChanged }: Props) {
  const [open, setOpen] = useState(false)
  const [isbn, setIsbn] = useState(book.edition?.isbn ?? '')
  const [pending, setPending] = useState<'save' | 'remove' | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const trigger = useRef<HTMLButtonElement>(null)
  const inputId = `isbn-${book.id}`

  async function change(remove: boolean) {
    if (pending) return
    setPending(remove ? 'remove' : 'save')
    setError('')
    setNotice('')
    try {
      const updated = remove
        ? (await removeEdition(book.id), { ...book, edition: null })
        : await saveEdition(book.id, isbn.trim())
      onUpdated(updated)
      setIsbn(updated.edition?.isbn ?? '')
      setNotice(remove ? 'ISBN and cover removed. The book is still in the catalog.'
        : updated.edition?.coverUrl ? 'Edition saved. The cover has been updated.'
        : 'Edition saved. No cover was found for this edition.')
    } catch (failure) {
      const status = failure instanceof ApiError ? failure.status : 0
      setError(status === 400 ? 'Enter a valid ISBN-10 or ISBN-13. Spaces and hyphens are allowed.'
        : status === 404 ? 'This book is no longer in the catalog. Refresh the page.'
        : status === 429 ? 'Please wait a second before another ISBN lookup.'
        : status === 401 || status === 403 ? 'Your session has changed. Sign in again if needed, then retry.'
        : status === 502 || status === 503 ? 'Book services are unavailable right now. Try again later; your saved edition has not changed.'
        : 'Could not confirm the change. Check your connection and retry, or refresh to see the saved edition.')
      if (status === 401 || status === 403) onSessionChanged()
    } finally { setPending(null) }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void change(false)
  }

  return (
    <div className="col-span-2 mt-3 text-sm sm:ml-32">
      <button ref={trigger} type="button" aria-expanded={open} aria-controls={`edition-form-${book.id}`}
        aria-label={`${book.edition ? 'Edit' : 'Add'} ISBN for ${book.title}`} disabled={pending !== null}
        onClick={() => { setOpen(!open); setIsbn(book.edition?.isbn ?? ''); setError(''); setNotice('') }}
        className="min-h-11 font-semibold text-accent hover:text-foreground disabled:opacity-50">
        {open ? 'Close ISBN editor' : book.edition ? 'Edit ISBN' : 'Add ISBN'}
      </button>
      {open && (
        <form id={`edition-form-${book.id}`} onSubmit={submit} className="max-w-lg border-t border-border pt-4">
          <fieldset disabled={pending !== null} className="disabled:opacity-60">
            <label htmlFor={inputId} className="block font-semibold">Edition ISBN</label>
            <p id={`${inputId}-help`} className="mt-2 leading-6 text-muted">Use the ISBN printed on your edition. This changes the cover association in the shared catalog, not the title or author.</p>
            <input id={inputId} name="isbn" type="text" required maxLength={32} autoComplete="off" spellCheck={false}
              aria-describedby={`${inputId}-help${error ? ` ${inputId}-error` : ''}`} aria-invalid={error ? true : undefined}
              value={isbn} onChange={(event) => { setIsbn(event.target.value); setError(''); setNotice('') }}
              className="mt-3 min-h-11 w-full border border-border bg-background px-3 text-base" />
            <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2">
              <button type="submit" className="min-h-11 border-b-2 border-accent font-semibold text-accent hover:border-foreground hover:text-foreground">{pending === 'save' ? 'Saving…' : 'Save ISBN'}</button>
              {book.edition && <button type="button" onClick={() => void change(true)} className="min-h-11 text-accent underline underline-offset-4 hover:text-foreground">{pending === 'remove' ? 'Removing…' : 'Remove ISBN'}</button>}
              <button type="button" onClick={() => { setOpen(false); setError(''); setNotice(''); trigger.current?.focus() }} className="min-h-11 text-muted hover:text-foreground">Cancel</button>
            </div>
          </fieldset>
        </form>
      )}
      {pending && <p role="status" className="mt-2 text-muted">{pending === 'save' ? 'Looking up this edition…' : 'Removing the association…'}</p>}
      {error && <p id={`${inputId}-error`} role="alert" className="mt-2 max-w-lg leading-6 text-accent">{error}</p>}
      {notice && <p role="status" className="mt-2 max-w-lg leading-6 text-muted">{notice}</p>}
    </div>
  )
}
