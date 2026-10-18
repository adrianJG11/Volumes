// Keep a stable 2:3 space for future cover images supplied by the catalog API.
export function BookCover() {
  return (
    <div aria-hidden="true" className="flex aspect-2/3 w-16 shrink-0 items-end border-l-[3px] border-cover-spine bg-cover p-2 sm:w-24 sm:p-3">
      <span className="font-serif text-xs italic text-muted sm:text-sm">No cover</span>
    </div>
  )
}
