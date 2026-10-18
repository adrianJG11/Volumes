import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { register, signIn, signOut, type Account } from './api'
import type { Session } from './useSession'

type Props = {
  session: Session
  onSignIn: (account: Account) => void
  onSignOut: () => void
  onRetry: () => void
}

export function AccountPanel({ session, onSignIn, onSignOut, onRetry }: Props) {
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const trigger = useRef<HTMLButtonElement>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setError('')
    setNotice('')
    if (mode === 'register' && (Array.from(password).length < 15 || new TextEncoder().encode(password).length > 72)) {
      setError('Use at least 15 characters and at most 72 UTF-8 bytes for your password.')
      return
    }
    setPending(true)
    try {
      if (mode === 'register') {
        await register(email, password)
        setMode('login')
        setNotice('Account created. Sign in to add books and change covers.')
      } else {
        onSignIn(await signIn(email, password))
        setOpen(false)
        setNotice('Signed in. You can now add books and change covers.')
      }
    } catch (failure) {
      const status = failure instanceof ApiError ? failure.status : 0
      setError(status === 401 ? 'Incorrect email or password.'
        : status === 409 ? 'This email already has an account. Sign in instead.'
        : status === 400 ? 'Enter a valid email and a password of at least 15 characters and at most 72 UTF-8 bytes.'
        : status === 403 ? 'Your session changed. Please try again.'
        : 'Could not complete the request. Check your connection and try again.')
    } finally {
      setPassword('')
      setPending(false)
    }
  }

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
      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
        {session.status === 'authenticated' ? (
          <>
            <p className="min-w-0 text-muted wrap-anywhere">{session.account.email}</p>
            <button type="button" disabled={pending} onClick={logout} className="min-h-11 font-semibold text-accent hover:text-foreground disabled:opacity-50">
              {pending ? 'Signing out…' : 'Sign out'}
            </button>
          </>
        ) : session.status === 'checking' ? <p className="text-muted">Checking session…</p>
          : session.status === 'error' ? (
            <><p>Could not check your session.</p><button type="button" onClick={onRetry} className="min-h-11 font-semibold text-accent hover:text-foreground">Retry session</button></>
          ) : (
            <>
              <p className="text-muted">Sign in to add books or change covers.</p>
              <button ref={trigger} type="button" aria-expanded={open} aria-controls="account-form" disabled={pending}
                onClick={() => { setOpen(!open); setError(''); setNotice(''); setPassword('') }}
                className="min-h-11 font-semibold text-accent hover:text-foreground">{open ? 'Close sign in' : 'Sign in'}</button>
            </>
          )}
      </div>
      {notice && <p role="status" className="mt-2 text-muted">{notice}</p>}
      {error && <p role="alert" className="mt-2 text-accent">{error}</p>}
      {open && session.status === 'anonymous' && (
        <form id="account-form" onSubmit={submit} className="max-w-md py-4">
          <h2 className="font-serif text-2xl">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
          <fieldset disabled={pending} className="mt-4 grid gap-4 disabled:opacity-60">
            <div>
              <label htmlFor="account-email" className="block font-semibold">Email</label>
              <input id="account-email" name="email" type="email" spellCheck={false} autoComplete="username" required maxLength={254} value={email}
                onChange={(event) => setEmail(event.target.value)} className="mt-2 min-h-11 w-full border border-border bg-background px-3 text-base" />
            </div>
            <div>
              <label htmlFor="account-password" className="block font-semibold">Password</label>
              <input id="account-password" name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required
                minLength={mode === 'register' ? 15 : undefined} maxLength={72} value={password}
                aria-describedby={mode === 'register' ? 'password-help' : undefined}
                onChange={(event) => setPassword(event.target.value)} className="mt-2 min-h-11 w-full border border-border bg-background px-3 text-base" />
              {mode === 'register' && <p id="password-help" className="mt-2 text-muted">Use at least 15 characters. Accented characters and emoji count toward the 72-byte limit.</p>}
            </div>
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
              <button type="submit" className="min-h-11 border-b-2 border-accent font-semibold text-accent hover:border-foreground hover:text-foreground">
                {pending ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
              </button>
              <button type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); setNotice(''); setPassword('') }}
                className="min-h-11 text-muted underline underline-offset-4 hover:text-foreground">{mode === 'login' ? 'Create an account' : 'I already have an account'}</button>
              <button type="button" onClick={() => { setOpen(false); setPassword(''); trigger.current?.focus() }} className="min-h-11 text-muted hover:text-foreground">Cancel</button>
            </div>
          </fieldset>
        </form>
      )}
    </section>
  )
}
