"use client";

import { useEffect, useState } from "react";
import { useRouter } from "@/i18n/navigation";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { getArtistDiscographyStatus } from "../api/catalogApi";

/** How often we ask, and for how long before giving up quietly (2s × 90 = three minutes). */
const POLL_INTERVAL_MS = 2000;
const MAX_ATTEMPTS = 90;

/**
 * Shown while the backend is still pulling an artist's discography from the metadata provider.
 *
 * Opening an artist page used to wait for that pull to finish inside the request — minutes, on an
 * artist the background walk hadn't reached, with the page stuck on its loading skeletons the whole
 * time. The page now renders whatever the catalog already has, immediately, and the artist jumps to
 * the front of the worker's queue; this tells the reader that more is coming and refreshes the page
 * once it has arrived, instead of leaving them looking at three songs wondering if that is all.
 *
 * Polling (rather than a socket) on purpose: it is one cheap local read every few seconds, only
 * while an incomplete artist page is actually open, and it stops the moment it is complete.
 */
export function CatalogSyncNotice({ artistId }: { artistId: string }) {
  const t = useTranslations("artist");
  const router = useRouter();
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    let attempts = 0;

    const poll = async () => {
      attempts += 1;
      try {
        const status = await getArtistDiscographyStatus(artistId);
        if (cancelled) return;
        if (status.complete) {
          setVisible(false);
          // Server Components hold the track and album lists, so the new music only appears once
          // the server re-renders the route.
          router.refresh();
          return;
        }
      } catch {
        // A failed poll is not worth telling the reader about — the next one is two seconds away.
      }
      if (cancelled) return;
      if (attempts >= MAX_ATTEMPTS) {
        // The provider is having a bad day. Stop asking rather than poll forever.
        setVisible(false);
        return;
      }
      timer = setTimeout(poll, POLL_INTERVAL_MS);
    };

    timer = setTimeout(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [artistId, router]);

  if (!visible) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-3 rounded-lg border border-border/60 bg-foreground/[0.03] px-4 py-3 text-sm text-muted-foreground"
    >
      <Loader2 aria-hidden className="size-4 shrink-0 animate-spin" />
      <span>{t("catalogSync")}</span>
    </div>
  );
}
