export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Request failed (${status})`)
    this.status = status
  }
}

async function request(path: string, options: RequestInit = {}, timeoutMs = 10_000): Promise<Response> {
  const response = await fetch(path, {
    ...options,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...options.headers },
    signal: options.signal
      ? AbortSignal.any([options.signal, AbortSignal.timeout(timeoutMs)])
      : AbortSignal.timeout(timeoutMs),
  })
  if (!response.ok) throw new ApiError(response.status)
  return response
}

export async function getJson(path: string, signal?: AbortSignal, timeoutMs?: number): Promise<unknown> {
  return (await request(path, { signal }, timeoutMs)).json()
}

export async function mutate(path: string, method: 'POST' | 'PUT' | 'PATCH' | 'DELETE', body?: unknown): Promise<unknown> {
  // Fetch a fresh token for each action, including after login rotates the session.
  const csrf = await getJson('/auth/csrf')
  if (typeof csrf !== 'object' || csrf === null || !('token' in csrf)
      || typeof csrf.token !== 'string' || !('headerName' in csrf) || csrf.headerName !== 'X-CSRF-TOKEN') {
    throw new Error('Unexpected CSRF response')
  }
  const form = body instanceof URLSearchParams
  const response = await request(path, {
    method,
    headers: {
      'X-CSRF-TOKEN': csrf.token,
      ...(body === undefined ? {} : { 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json' }),
    },
    body: body === undefined ? undefined : form ? body.toString() : JSON.stringify(body),
  })
  return response.status === 204 ? undefined : response.json()
}
