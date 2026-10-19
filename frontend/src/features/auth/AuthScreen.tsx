import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { register, signIn } from './api'

export function AuthScreen({ mode }: { mode: 'login' | 'register' }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setError('')

    if (mode === 'register' && (Array.from(password).length < 15 || new TextEncoder().encode(password).length > 72)) {
      setError('Use at least 15 characters and at most 72 UTF-8 bytes for your password.')
      return
    }
    setPending(true)
    try {
      if (mode === 'register') {
        await register(email, password)
        window.location.replace('/login?registered=1')
      } else {
        await signIn(email, password)
        window.location.replace('/')
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


  return (
    <section className="max-w-md">
      {mode === 'login' && new URLSearchParams(window.location.search).has('registered') &&
        <p role="status" className="mt-4 text-muted">Account created. Sign in to start your library.</p>}
      {error && <p role="alert" className="mt-4 text-accent">{error}</p>}
        <form id="account-form" onSubmit={submit} className="max-w-md py-4">

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

              <a href={mode === 'login' ? '/register' : '/login'} className="inline-flex min-h-11 items-center text-muted underline underline-offset-4">{mode === 'login' ? 'Create an account' : 'I already have an account'}</a>
            </div>
          </fieldset>
        </form>

    </section>
  )
}
