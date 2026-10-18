import { getJson, mutate } from '../../api/client'

export type BookEdition = {
  isbn: string
  openLibraryId: string
  coverId: number | null
  coverUrl: string | null
  sourceUrl: string
}

function isEdition(value: unknown): value is BookEdition {
  if (typeof value !== 'object' || value === null) return false
  const edition = value as Record<string, unknown>
  return typeof edition.isbn === 'string' && /^97[89]\d{10}$/.test(edition.isbn)
    && typeof edition.openLibraryId === 'string' && /^OL\d+M$/.test(edition.openLibraryId)
    && (edition.coverId === null || (typeof edition.coverId === 'number' && Number.isSafeInteger(edition.coverId) && edition.coverId > 0))
    && edition.sourceUrl === `https://openlibrary.org/books/${edition.openLibraryId}`
    && edition.coverUrl === (edition.coverId === null ? null : `https://covers.openlibrary.org/b/id/${edition.coverId}-M.jpg?default=false`)
}

export type Book = {
  id: string
  title: string
  author: string
  genre: string
  pages: number
  edition?: BookEdition | null
}

function isBook(value: unknown): value is Book {
  if (typeof value !== 'object' || value === null) return false
  const book = value as Record<string, unknown>
  return typeof book.id === 'string' && typeof book.title === 'string'
    && typeof book.author === 'string' && typeof book.genre === 'string'
    && typeof book.pages === 'number' && Number.isInteger(book.pages) && book.pages > 0
    && (book.edition == null || isEdition(book.edition))
}

export async function getBooks(signal: AbortSignal): Promise<Book[]> {
  const data = await getJson('/books', signal)
  // TypeScript checks our code, not the JSON arriving over the network.
  if (!Array.isArray(data) || !data.every(isBook)) {
    throw new Error('Unexpected catalog response')
  }
  return data
}

export async function saveEdition(id: string, isbn: string): Promise<Book> {
  const data = await mutate(`/books/${encodeURIComponent(id)}/edition`, 'PUT', { isbn })
  if (!isBook(data) || data.id !== id) throw new Error('Unexpected book response')
  return data
}

export async function removeEdition(id: string): Promise<void> {
  await mutate(`/books/${encodeURIComponent(id)}/edition`, 'DELETE')
}
