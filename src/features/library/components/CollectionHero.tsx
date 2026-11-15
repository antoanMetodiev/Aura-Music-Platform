import type { ReactNode } from "react";

interface CollectionHeroProps {
  /** Cover: an <ArtworkImage> or a drawn tile (Liked songs). */
  cover: ReactNode;
  eyebrow: string;
  /** Plain text, or an editable control on an own playlist. */
  title: ReactNode;
  meta?: ReactNode;
}

/** Header for Liked songs and playlists — same proportions as the album hero. */
export function CollectionHero({ cover, eyebrow, title, meta }: CollectionHeroProps) {
  return (
    <header className="flex flex-col items-center gap-6 px-3 text-center sm:flex-row sm:items-end sm:text-left">
      <div className="size-48 shrink-0 shadow-[0_24px_60px_-20px_rgba(0,0,0,0.9)] sm:size-56">{cover}</div>
      <div className="flex min-w-0 flex-col gap-3">
        <p className="text-xs font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{eyebrow}</p>
        <div className="text-3xl font-bold tracking-tight text-balance sm:text-5xl">{title}</div>
        {meta && <div className="text-sm text-muted-foreground">{meta}</div>}
      </div>
    </header>
  );
}

/** The Liked songs cover: a drawn gradient tile with a heart, no artwork needed. */
export function LikedCover({ children }: { children: ReactNode }) {
  return (
    <div className="grid size-full place-items-center rounded-md bg-gradient-to-br from-[#4c82ff] via-[#2f6bff] to-[#0b2a5b]">
      {children}
    </div>
  );
}
