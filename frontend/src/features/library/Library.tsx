import { useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { clearRating, getLibrary, saveRating, type Entry } from './api'

function Rating({ entry, onSaved, onSessionChanged }: { entry: Entry; onSaved: (entry: Entry) => void; onSessionChanged: () => void }) {
  const [draft, setDraft] = useState(entry.rating?.toString() ?? '')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  async function change(clear: boolean) {
    if (pending) return
    setPending(true)
    setError('')
    setNotice('')
    try {
      if (clear) {
        await clearRating(entry.bookId)
        onSaved({ ...entry, rating: null })
        setDraft('')
      } else {
        const saved = await saveRating(entry.bookId, Number(draft))
        onSaved(saved)
        setDraft(saved.rating?.toString() ?? '')
      }
      setNotice(clear ? 'Rating cleared.' : 'Rating saved.')
    } catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) onSessionChanged()
      else setError('Could not save your rating. Your saved rating has not changed. Please try again.')
    } finally { setPending(false) }
  }
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); void change(false) }
  return <form onSubmit={submit} className="mt-4">
    <p className="text-sm text-muted">Saved rating: {entry.rating === null ? 'Not rated' : entry.rating + ' / 5'}</p>
    <fieldset disabled={pending} className="mt-3 flex flex-wrap items-end gap-4 disabled:opacity-50">
      <div>
        <label htmlFor={'rating-' + entry.bookId} className="block text-sm font-semibold">Your rating for {entry.title}</label>
        <select id={'rating-' + entry.bookId} value={draft} required onChange={(event) => setDraft(event.target.value)}
          className="mt-2 min-h-11 border border-border bg-background px-3">
          <option value="" disabled>Choose a rating</option>
          {[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value} / 5</option>)}
        </select>
      </div>
      <button type="submit" disabled={!draft} className="min-h-11 font-semibold text-accent disabled:opacity-50">{pending ? 'Saving…' : 'Save rating'}</button>
      <button type="button" disabled={entry.rating === null} onClick={() => void change(true)}
        className="min-h-11 text-muted underline disabled:opacity-50">Clear rating</button>
    </fieldset>
    {notice && <p role="status" className="mt-2 text-sm text-muted">{notice}</p>}
    {error && <p role="alert" className="mt-2 text-sm text-accent">{error}</p>}
  </form>
}

export function Library({ onSessionChanged }: { onSessionChanged: () => void }) {
  const [entries, setEntries] = useState<Entry[] | null>(null)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getLibrary(controller.signal).then(
      (items) => { if (!controller.signal.aborted) setEntries(items) },
      (failure) => {
        if (controller.signal.aborted) return
        setError(true)
        if (failure instanceof ApiError && [401, 403].includes(failure.status)) onSessionChanged()
      },
    )
    return () => controller.abort()
  }, [attempt, onSessionChanged])
  function saved(entry: Entry) {
    setEntries((items) => items?.map((item) => item.bookId === entry.bookId ? entry : item) ?? null)
  }
  return <section aria-label="Your books" className="mt-10">
    {error ? <div>
      <p role="alert">Could not load your library.</p>
      <button type="button" onClick={() => { setError(false); setAttempt((value) => value + 1) }} className="min-h-11 text-accent underline">Retry library</button>
    </div> : entries === null ? <p role="status">Loading your library…</p>
      : entries.length === 0 ? <p>No books in your library yet. <a href="/" className="text-accent underline">Browse the catalog</a>.</p>
      : <ul className="divide-y divide-border">{entries.map((entry) => <li key={entry.bookId} className="py-6">
        <h2 className="font-serif text-2xl wrap-anywhere">{entry.title}</h2>
        <p className="mt-2 text-muted wrap-anywhere">{entry.author}</p>
        <Rating entry={entry} onSaved={saved} onSessionChanged={onSessionChanged} />
      </li>)}</ul>}
  </section>
}
