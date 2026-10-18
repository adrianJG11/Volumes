import { getJson } from '../../api/client'

export type Book = {
  id: string
  title: string
  author: string
  genre: string
  pages: number
}

function isBook(value: unknown): value is Book {
  if (typeof value !== 'object' || value === null) return false
  const book = value as Record<string, unknown>
  return typeof book.id === 'string' && typeof book.title === 'string'
    && typeof book.author === 'string' && typeof book.genre === 'string'
    && typeof book.pages === 'number' && Number.isInteger(book.pages) && book.pages > 0
}

export async function getBooks(signal: AbortSignal): Promise<Book[]> {
  const data = await getJson('/books', signal)
  // TypeScript checks our code, not the JSON arriving over the network.
  if (!Array.isArray(data) || !data.every(isBook)) {
    throw new Error('Unexpected catalog response')
  }
  return data
}
