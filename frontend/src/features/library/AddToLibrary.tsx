import { useState } from 'react'
import { ApiError } from '../../api/client'
import { addToLibrary } from './api'

export function AddToLibrary({ bookId, title, onSessionChanged }: { bookId: string; title: string; onSessionChanged: () => void }) {
  const [state, setState] = useState<'ready' | 'pending' | 'added'>('ready')
  const [error, setError] = useState('')
  async function add() {
    if (state !== 'ready') return
    setState('pending')
    setError('')
    try {
      await addToLibrary(bookId)
      setState('added')
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) setState('added')
      else {
        setState('ready')
        if (failure instanceof ApiError && [401, 403].includes(failure.status)) onSessionChanged()
        else setError('Could not add this book. Please try again.')
      }
    }
  }
  return <div className="col-start-2 mt-3 text-sm">
    {state === 'added' ? <p role="status">In your library. <a href="/library" className="text-accent underline">View my library</a></p>
      : <button type="button" disabled={state === 'pending'} onClick={add}
        aria-label={'Add ' + title + ' to my library'}
        className="min-h-11 font-semibold text-accent hover:text-foreground disabled:opacity-50">
        {state === 'pending' ? 'Adding…' : 'Add to my library'}
      </button>}
    {error && <p role="alert" className="text-accent">{error}</p>}
  </div>
}
