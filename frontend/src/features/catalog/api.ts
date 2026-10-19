import { getJson, mutate } from '../../api/client'

export type BookEdition = {
  isbn: string
  openLibraryId: string | null
  coverUrl: string | null
  sourceUrl: string | null
}

// Same hosts the server allows: Open Library covers and Apple's iTunes artwork.
export function isCoverUrl(value: unknown): value is string {
  if (typeof value !== 'string' || value.length > 1000) return false
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && url.port === '' && url.username === ''
      && (url.hostname === 'covers.openlibrary.org' || /^is\d+-ssl\.mzstatic\.com$/.test(url.hostname))
  } catch {
    return false
  }
}

function isEdition(value: unknown): value is BookEdition {
  if (typeof value !== 'object' || value === null) return false
  const edition = value as Record<string, unknown>
  const id = edition.openLibraryId
  return typeof edition.isbn === 'string' && /^97[89]\d{10}$/.test(edition.isbn)
    && (id === null || (typeof id === 'string' && /^OL\d+M$/.test(id)))
    && edition.sourceUrl === (id === null ? null : `https://openlibrary.org/books/${id}`)
    && (edition.coverUrl === null || isCoverUrl(edition.coverUrl))
}

export type Book = {
  id: string
  title: string
  author: string
  genre: string
  pages: number
  edition?: BookEdition | null
}

export function isBook(value: unknown): value is Book {
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

export type BookSuggestion = {
  title: string
  author: string | null
  genre: string | null
  pages: number | null
  isbn: string | null
  coverUrl: string | null
  firstPublishYear: number | null
}

function isSuggestion(value: unknown): value is BookSuggestion {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  const optional = (key: string, type: 'string' | 'number') => item[key] === null || typeof item[key] === type
  return typeof item.title === 'string' && optional('author', 'string') && optional('genre', 'string')
    && optional('pages', 'number') && optional('firstPublishYear', 'number')
    && (item.isbn === null || (typeof item.isbn === 'string' && /^97[89]\d{10}$/.test(item.isbn)))
    && (item.coverUrl === null || isCoverUrl(item.coverUrl))
}

export async function searchBooks(query: string, signal: AbortSignal): Promise<BookSuggestion[]> {
  // The first search for a popular work also downloads its edition list, which can take several seconds.
  const data = await getJson(`/books/search?q=${encodeURIComponent(query)}`, signal, 20_000)
  if (!Array.isArray(data) || !data.every(isSuggestion)) throw new Error('Unexpected search response')
  return data
}

export type NewBook = { title: string; author: string; genre: string; pages: number }

export async function createBook(book: NewBook): Promise<Book> {
  const data = await mutate('/books', 'POST', book)
  if (!isBook(data)) throw new Error('Unexpected book response')
  return data
}
