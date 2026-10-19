import { useEffect, useRef } from 'react'
import { AuthScreen } from './features/auth/AuthScreen'
import { AccountPanel } from './features/auth/AccountPanel'
import { useSession } from './features/auth/useSession'
import { Library } from './features/library/Library'

function App() {
  const { session, setSession, refresh } = useSession()
  const path = window.location.pathname
  const mode = path === '/login' ? 'login' : path === '/register' ? 'register' : undefined
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    document.title = (mode === 'login' ? 'Sign in' : mode === 'register' ? 'Create account' : 'My library') + ' · Volumes'
    heading.current?.focus()
  }, [mode, session.status])
  useEffect(() => {
    if (mode && session.status === 'authenticated') window.location.replace('/')
    if (!mode && session.status === 'anonymous') window.location.replace('/login')
  }, [mode, session.status])
  return (
    <div className="min-h-dvh bg-background text-foreground">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-10 focus:bg-background focus:p-3 focus:text-accent">
        Skip to content
      </a>
      <header className="mx-auto max-w-6xl px-6 pt-8 sm:px-12 sm:pt-12">
        <div className="flex flex-wrap items-end justify-between gap-5 border-b-2 border-foreground pb-6">
          <span className="font-serif text-6xl leading-none sm:text-8xl">Volumes<span className="text-accent">.</span></span>
          <span className="pb-1 text-sm text-muted">A personal reading library</span>
        </div>
        {session.status === 'authenticated' && <AccountPanel session={session}
          onSignOut={() => setSession({ status: 'anonymous' })} onRetry={refresh} />}
      </header>
      <main id="main" tabIndex={-1} className="mx-auto max-w-6xl px-6 pt-8 pb-20 sm:px-12 sm:pt-16">
        {session.status === 'checking' ? <p role="status">Checking session…</p>
          : session.status === 'error' ? <div>
            <p role="alert">Could not check your session.</p>
            <button onClick={refresh} className="min-h-11 text-accent underline">Retry session</button>
          </div> : mode && session.status === 'anonymous' ? <>
            <h1 ref={heading} tabIndex={-1} className="focus:outline-none font-serif text-4xl sm:text-6xl">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
            <AuthScreen mode={mode} />
          </> : !mode && session.status === 'authenticated' ? <>
            <h1 ref={heading} tabIndex={-1} className="focus:outline-none font-serif text-4xl sm:text-6xl">My library</h1>
            <p className="mt-4 text-muted">The books you want to read, are reading, and have finished.</p>
            <Library key={session.account.id} onSessionChanged={refresh} />
          </> : <p role="status">Opening your library…</p>}
      </main>
    </div>
  )
}

export default App
