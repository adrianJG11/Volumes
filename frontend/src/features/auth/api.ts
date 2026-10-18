import { ApiError, getJson, mutate } from '../../api/client'

export type Account = { id: string; email: string }

function accountFrom(value: unknown): Account {
  if (typeof value !== 'object' || value === null || !('id' in value) || typeof value.id !== 'string'
      || !('email' in value) || typeof value.email !== 'string') throw new Error('Unexpected account response')
  return { id: value.id, email: value.email }
}

export async function currentAccount(signal?: AbortSignal): Promise<Account | null> {
  try {
    return accountFrom(await getJson('/auth/me', signal))
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export async function signIn(email: string, password: string): Promise<Account> {
  await mutate('/auth/login', 'POST', new URLSearchParams({ email: email.trim(), password }))
  await getJson('/auth/csrf')
  const account = await currentAccount()
  if (!account) throw new Error('Session could not be loaded')
  return account
}

export async function register(email: string, password: string): Promise<void> {
  await mutate('/auth/register', 'POST', { email: email.trim(), password })
}

export async function signOut(): Promise<void> {
  await mutate('/auth/logout', 'POST')
  // Logout has already succeeded; a token refresh failure must not restore old account state.
  await getJson('/auth/csrf').catch(() => undefined)
}
