import { AccountPanel } from './features/auth/AccountPanel'
import { useSession } from './features/auth/useSession'
import { Catalog } from './features/catalog/Catalog'

function App() {
  const { session, setSession, refresh } = useSession()
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
        <nav aria-label="Main navigation" className="flex border-b border-border">
          <a href="#main" aria-current="page" className="border-b-2 border-accent py-4 text-sm font-semibold text-accent hover:text-foreground">Catalog</a>
        </nav>
        <AccountPanel session={session}
          onSignIn={(account) => setSession({ status: 'authenticated', account })}
          onSignOut={() => setSession({ status: 'anonymous' })} onRetry={refresh} />
      </header>
      <main id="main" tabIndex={-1} className="mx-auto max-w-6xl px-6 pt-8 pb-20 sm:px-12 sm:pt-16">
        <div className="grid gap-5 sm:grid-cols-[1fr_18rem] sm:items-end">
          <h1 className="font-serif text-4xl leading-tight text-balance sm:text-6xl">The collection</h1>
          <p className="max-w-md text-base leading-7 text-muted text-pretty">
            Browse the books. Find something to spend time with.
          </p>
        </div>
        <Catalog accountId={session.status === 'authenticated' ? session.account.id : undefined} onSessionChanged={refresh} />
      </main>
    </div>
  )
}

export default App
