import { useEffect, useState } from 'react'
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

  function refresh() {
    setSession({ status: 'checking' })
    setAttempt((value) => value + 1)
  }
  return { session, setSession, refresh }
}
