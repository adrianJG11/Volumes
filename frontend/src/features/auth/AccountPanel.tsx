import { useState } from 'react'
import { signOut } from './api'
import type { Session } from './useSession'

type Props = {
  session: Session
  onSignOut: () => void
  onRetry: () => void
}
export function AccountPanel({ session, onSignOut, onRetry }: Props) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  async function logout() {
    setPending(true)
    setError('')
    try {
      await signOut()
      onSignOut()
      setNotice('Signed out.')
    } catch {
      setError('Could not sign out. Check your connection and try again.')
    } finally { setPending(false) }
  }


  return (
    <section aria-label="Account" className="border-b border-border py-4 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        {session.status === 'authenticated' ? <>
          <p className="min-w-0 text-muted wrap-anywhere">{session.account.email}</p>
          <button type="button" disabled={pending} onClick={logout} className="min-h-11 font-semibold text-accent hover:text-foreground disabled:opacity-50">
            {pending ? 'Signing out…' : 'Sign out'}
          </button>
        </> : session.status === 'checking' ? <p role="status">Checking session…</p>
          : session.status === 'error' ? <>
            <p>Could not check your session.</p>
            <button type="button" onClick={onRetry} className="min-h-11 font-semibold text-accent">Retry session</button>
          </> : <a href="/login" className="inline-flex min-h-11 items-center font-semibold text-accent hover:text-foreground">Sign in</a>}
      </div>
      {notice && <p role="status" className="mt-2 text-muted">{notice}</p>}
      {error && <p role="alert" className="mt-2 text-accent">{error}</p>}
    </section>
  )
}
