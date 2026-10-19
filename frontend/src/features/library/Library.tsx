import { useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { AddBook } from '../catalog/AddBook'
import { BookCover } from '../catalog/BookCover'
import { EditionEditor } from '../catalog/EditionEditor'
import { clearRating, getLibrary, saveRating, saveStatus, statuses, type ReadingStatus, type Entry } from './api'

function Rating({ entry, onSaved, onSessionChanged }: { entry: Entry; onSaved: (entry: Entry) => void; onSessionChanged: () => void }) {
  const [draft, setDraft] = useState(entry.rating?.toString() ?? '')
  const [hovered, setHovered] = useState(0)
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
  return <form onSubmit={submit} className="mt-5">
    <fieldset disabled={pending} className="disabled:opacity-50">
      <legend className="text-sm font-semibold">Your rating<span className="sr-only"> for {entry.title}</span></legend>
      <div className="mt-1 flex flex-wrap items-center gap-x-3">
        <div className="flex" onMouseLeave={() => setHovered(0)}>
          {[1, 2, 3, 4, 5].map((value) => <label key={value} className="relative flex size-11 cursor-pointer items-center justify-center" onMouseEnter={() => setHovered(value)}>
            <input type="radio" name={'rating-' + entry.bookId} value={value} checked={draft === String(value)}
              onChange={() => { setDraft(String(value)); setNotice(''); setError('') }}
              aria-label={value + (value === 1 ? ' star' : ' stars')}
              className="peer absolute inset-0 size-full cursor-pointer opacity-0" />
            <svg viewBox="0 0 24 24" aria-hidden="true"
              className={'size-8 rounded-sm peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4 peer-focus-visible:outline-accent ' + (value <= (hovered || Number(draft)) ? 'text-accent' : 'text-muted')}
              fill={value <= (hovered || Number(draft)) ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round">
              <path d="m12 2.5 2.93 5.94 6.56.95-4.75 4.63 1.12 6.54L12 17.47l-5.86 3.09 1.12-6.54L2.51 9.39l6.56-.95Z" />
            </svg>
          </label>)}
        </div>
        <span className="text-sm text-muted tabular-nums">{draft ? draft + ' / 5' : 'Not rated'}</span>
      </div>
      <div className="flex flex-wrap items-center gap-x-5">
        {draft && Number(draft) !== entry.rating && <button type="submit" className="min-h-11 font-semibold text-accent hover:text-foreground">{pending ? 'Saving…' : 'Save rating'}</button>}
        {entry.rating !== null && <button type="button" onClick={() => void change(true)}
          className="min-h-11 text-sm text-muted underline underline-offset-4 hover:text-accent">Clear rating</button>}
      </div>
    </fieldset>
    <p className="sr-only">Saved rating: {entry.rating === null ? 'Not rated' : entry.rating + ' / 5'}</p>
    {draft && Number(draft) !== entry.rating && <p className="text-xs text-muted">Unsaved rating</p>}
    {notice && <p role="status" className="mt-1 text-sm text-muted">{notice}</p>}
    {error && <p role="alert" className="mt-2 text-sm text-accent">{error}</p>}
  </form>

}

function Reading({ entry, onSaved, onSessionChanged }: { entry: Entry; onSaved: (entry: Entry) => void; onSessionChanged: () => void }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function change(status: ReadingStatus) {
    setPending(true)
    setError('')
    try { onSaved(await saveStatus(entry.bookId, status)) }
    catch (failure) {
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) onSessionChanged()
      else setError('Could not change your reading status. Please try again.')
    } finally { setPending(false) }
  }
  return <div className="mt-4">
    <label htmlFor={'status-' + entry.bookId} className="block text-sm font-semibold">Reading status<span className="sr-only"> for {entry.title}</span></label>
    <select id={'status-' + entry.bookId} disabled={pending} value={entry.status}
      onChange={(event) => void change(event.target.value as ReadingStatus)}
      className="mt-2 min-h-11 border border-border bg-background px-3 disabled:opacity-50">
      {Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </select>
    {pending && <p role="status" className="mt-2 text-sm text-muted">Saving reading status…</p>}
    {error && <p role="alert" className="mt-2 text-sm text-accent">{error}</p>}
  </div>
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
  return <section aria-label="Your books" className="mt-8">
    <AddBook onAdded={() => { setError(false); setAttempt((value) => value + 1) }} onSessionChanged={onSessionChanged} />
    {error ? <div>
      <p role="alert">Could not load your library.</p>
      <button type="button" onClick={() => { setError(false); setAttempt((value) => value + 1) }} className="min-h-11 text-accent underline">Retry library</button>
    </div> : entries === null ? <p role="status">Loading your library…</p>
      : entries.length === 0 ? <p className="py-8 text-muted">Your library is empty. Add your first book above.</p>
      : <ul className="divide-y divide-border">{entries.map((entry) => <li key={entry.bookId} className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-5 py-7 sm:gap-x-8">
        <BookCover key={entry.edition?.coverUrl ?? 'missing'} url={entry.edition?.coverUrl} />
        <div className="min-w-0">
          <h2 className="font-serif text-2xl wrap-anywhere">{entry.title}</h2>
          <p className="mt-2 text-muted wrap-anywhere">{entry.author}</p>
          <p className="mt-2 text-sm text-muted">{entry.genre} · {entry.pages} pages</p>
        </div>
        <div className="col-span-2 sm:col-span-1 sm:col-start-2">
          <Reading entry={entry} onSaved={saved} onSessionChanged={onSessionChanged} />
          <Rating entry={entry} onSaved={saved} onSessionChanged={onSessionChanged} />
        </div>
        <EditionEditor book={{ ...entry, id: entry.bookId }} onUpdated={(book) => saved({ ...entry, ...book })} onSessionChanged={onSessionChanged} />
      </li>)}</ul>}
  </section>
}
