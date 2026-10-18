function App() {
  return (
    <div className="min-h-dvh bg-background text-foreground">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:rounded-md focus:bg-white focus:p-3 focus:text-accent"
      >
        Skip to content
      </a>
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-5xl items-center gap-3 px-6 py-6 sm:px-10">
          <svg aria-hidden="true" viewBox="0 0 28 28" className="size-7 text-accent" fill="none">
            <path d="M5 5v18M12 5v18M19 5l5 18" stroke="currentColor" strokeWidth="3" />
          </svg>
          <span className="text-xl font-semibold">Volumes</span>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="mx-auto max-w-5xl px-6 py-20 sm:px-10 sm:py-28">
        <h1 className="max-w-2xl font-serif text-5xl leading-tight text-balance sm:text-7xl">
          Your books, at your pace.
        </h1>
        <p className="mt-6 max-w-lg text-lg leading-8 text-muted text-pretty">
          Keep a place for the books you want to read, the ones you’re reading,
          and the stories you’ve finished.
        </p>
      </main>
    </div>
  )
}

export default App
