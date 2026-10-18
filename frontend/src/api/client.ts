export async function getJson(path: string, signal: AbortSignal): Promise<unknown> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
  })

  // fetch rejects network failures, but HTTP 4xx/5xx need an explicit check.
  if (!response.ok) throw new Error(`Request failed (${response.status})`)
  return response.json()
}
