import { isBook, type Book } from '../catalog/api'
import { getJson, mutate } from '../../api/client'

export const statuses = { PENDING: 'Want to read', READING: 'Reading', FINISHED: 'Finished' } as const
export type ReadingStatus = keyof typeof statuses
export type Entry = Omit<Book, 'id'> & { bookId: string; rating: number | null; status: ReadingStatus }

function entry(data: unknown): Entry {
  if (typeof data !== 'object' || data === null) throw new Error('Unexpected library entry')
  const item = data as Record<string, unknown>
  if (!isBook({ ...item, id: item.bookId }) || typeof item.status !== 'string' || !Object.hasOwn(statuses, item.status)
    || !(item.rating === null || (typeof item.rating === 'number' && Number.isInteger(item.rating) && item.rating >= 1 && item.rating <= 5))) {
    throw new Error('Unexpected library entry')
  }
  return item as Entry
}

export async function getLibrary(signal: AbortSignal): Promise<Entry[]> {
  const data = await getJson('/me/library', signal)
  if (!Array.isArray(data)) throw new Error('Unexpected library response')
  return data.map(entry)
}

export async function addToLibrary(bookId: string): Promise<void> {
  await mutate('/me/library', 'POST', { bookId })
}

export async function saveRating(bookId: string, rating: number): Promise<Entry> {
  const result = entry(await mutate('/me/library/' + encodeURIComponent(bookId) + '/rating', 'PUT', { rating }))
  if (result.bookId !== bookId) throw new Error('Unexpected library entry')
  return result
}

export async function clearRating(bookId: string): Promise<void> {
  await mutate('/me/library/' + encodeURIComponent(bookId) + '/rating', 'DELETE')
}

export async function saveStatus(bookId: string, status: ReadingStatus): Promise<Entry> {
  const result = entry(await mutate('/me/library/' + encodeURIComponent(bookId), 'PATCH', { status }))
  if (result.bookId !== bookId) throw new Error('Unexpected library entry')
  return result
}
