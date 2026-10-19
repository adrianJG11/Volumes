import { useEffect, useRef } from 'react'
import { AuthScreen } from './features/auth/AuthScreen'
import { AccountPanel } from './features/auth/AccountPanel'
import { useSession } from './features/auth/useSession'
import { Library } from './features/library/Library'
import { Catalog } from './features/catalog/Catalog'

function App() {
  const { session, setSession, refresh } = useSession()
  const path = window.location.pathname
  const library = path === '/library'
  const mode = path === '/login' ? 'login' : path === '/register' ? 'register' : undefined
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    document.title = (mode === 'login' ? 'Sign in' : mode === 'register' ? 'Create account' : library ? 'My library' : 'Catalog') + ' · Volumes'
    heading.current?.focus()
  }, [mode, library])
  useEffect(() => {
    if (mode && session.status === 'authenticated') window.location.replace('/')
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
        <nav aria-label="Main navigation" className="flex gap-6 border-b border-border">
          <a href="/" aria-current={!mode && !library ? 'page' : undefined} className="border-b-2 border-transparent py-4 text-sm font-semibold text-accent hover:text-foreground aria-[current=page]:border-accent">Catalog</a>
          {session.status === 'authenticated' && <a href="/library" aria-current={library ? 'page' : undefined} className="border-b-2 border-transparent py-4 text-sm font-semibold text-accent hover:text-foreground aria-[current=page]:border-accent">My library</a>}
        </nav>
        <AccountPanel session={session}
          onSignOut={() => setSession({ status: 'anonymous' })} onRetry={refresh} />
      </header>
      <main id="main" tabIndex={-1} className="mx-auto max-w-6xl px-6 pt-8 pb-20 sm:px-12 sm:pt-16">
        {mode ? <>
          <h1 ref={heading} tabIndex={-1} className="focus:outline-none font-serif text-4xl sm:text-6xl">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
          {session.status === 'anonymous' && <AuthScreen mode={mode} />}
        </> : library ? <>
          <h1 ref={heading} tabIndex={-1} className="focus:outline-none font-serif text-4xl sm:text-6xl">My library</h1>
          {session.status === 'authenticated' ? <Library key={session.account.id} onSessionChanged={refresh} />
            : session.status === 'anonymous' ? <p className="mt-6"><a href="/login" className="text-accent underline">Sign in</a> to view your library.</p> : null}
        </> : <>
        <div className="grid gap-5 sm:grid-cols-[1fr_18rem] sm:items-end">
          <h1 ref={heading} tabIndex={-1} className="focus:outline-none font-serif text-4xl leading-tight text-balance sm:text-6xl">The collection</h1>
          <p className="max-w-md text-base leading-7 text-muted text-pretty">
            Browse the books. Find something to spend time with.
          </p>
        </div>
        <Catalog accountId={session.status === 'authenticated' ? session.account.id : undefined} onSessionChanged={refresh} />
        </>}
      </main>
    </div>
  )
}

export default App
