import { useCallback, useEffect, useState } from 'react'
import { currentAccount, type Account } from './api'

export type Session =
  | { status: 'checking' | 'error' | 'anonymous' }
  | { status: 'authenticated'; account: Account }

export function useSession() {
  const [session, setSession] = useState<Session>({ status: 'checking' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    currentAccount(controller.signal).then(
      (account) => {
        if (!controller.signal.aborted) setSession(account ? { status: 'authenticated', account } : { status: 'anonymous' })
      },
      () => { if (!controller.signal.aborted) setSession({ status: 'error' }) },
    )
    return () => controller.abort()
  }, [attempt])

  const refresh = useCallback(() => {
    setSession({ status: 'checking' })
    setAttempt((value) => value + 1)
  }, [])
  useEffect(() => {
    // Back/Forward may restore a document from the browser cache after another tab signed out.
    const restored = (event: PageTransitionEvent) => { if (event.persisted) refresh() }
    window.addEventListener('pageshow', restored)
    return () => window.removeEventListener('pageshow', restored)
  }, [refresh])
  return { session, setSession, refresh }
}
