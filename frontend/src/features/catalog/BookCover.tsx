import { useState } from 'react'

export function BookCover({ url }: { url?: string | null }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const showImage = Boolean(url && url !== failedUrl)

  return (
    <div aria-hidden="true" className="relative aspect-2/3 w-16 shrink-0 self-start sm:w-24">
      <div className="absolute inset-0 flex items-end border-l-[3px] border-cover-spine bg-cover p-2 sm:p-3">
        <span className="font-serif text-xs italic text-muted sm:text-sm">No cover</span>
      </div>
      {showImage && (
        <img
          key={url}
          src={url ?? undefined}
          alt=""
          width={96}
          height={144}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={() => setFailedUrl(url ?? null)}
          className="absolute inset-0 h-full w-full bg-background object-contain object-top"
        />
      )}
    </div>
  )
}
