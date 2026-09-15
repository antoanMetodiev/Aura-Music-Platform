import { Headphones, Play } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { routes } from "@/config/routes";
import { formatCount } from "@/lib/utils/format";
import type { ArtistAbout } from "@/types/catalog";
import { ExpandableText } from "./ExpandableText";
import { ExternalLinkIcon } from "./ExternalLinkIcon";
import { HorizontalSection } from "./HorizontalSection";
import { MediaCard } from "./MediaCard";

/** Display names for the link types the backend classifies (`ArtistAboutService.HOST_TYPES`). */
const LINK_LABELS: Record<string, string> = {
  instagram: "Instagram",
  facebook: "Facebook",
  x: "X",
  tiktok: "TikTok",
  youtube: "YouTube",
  soundcloud: "SoundCloud",
  bandcamp: "Bandcamp",
  spotify: "Spotify",
  "apple-music": "Apple Music",
  wikipedia: "Wikipedia",
  discogs: "Discogs",
  lastfm: "Last.fm",
  threads: "Threads",
  vk: "VK",
  telegram: "Telegram",
};

const SOURCE_LABELS: Record<string, string> = { LASTFM: "Last.fm", DISCOGS: "Discogs" };

/**
 * Artist page "About" (FRONTEND.md §1.1): biography with a show-more clamp, listener stats, genre
 * tags, outside links, and a "fans also like" rail of similar artists we have in the catalog.
 * Server component — the only interactivity is the clamp toggle inside ExpandableText.
 */
export async function ArtistAboutSection({ about }: { about: ArtistAbout }) {
  const t = await getTranslations("artist");
  const bio = about.biography;
  const hasStats = about.listeners !== null || about.playcount !== null;
  const similarInCatalog = about.similar.filter((s) => s.artist !== null);
  const similarElsewhere = about.similar.filter((s) => s.artist === null).map((s) => s.name);

  const hasFacts = !!bio || hasStats || about.tags.length > 0 || about.links.length > 0;
  if (!hasFacts && similarInCatalog.length === 0) return null;

  return (
    <div className="flex flex-col gap-10">
      {hasFacts && (
        <section aria-labelledby="artist-about-heading">
          <h2 id="artist-about-heading" className="mb-4 text-xl font-bold tracking-tight sm:text-2xl">
            {t("about")}
          </h2>

          <div className="grid gap-8 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            {/* Biography */}
            {bio ? (
              <div className="min-w-0">
                <ExpandableText text={bio.text} lines={7} showMoreLabel={t("showMore")} showLessLabel={t("showLess")} />
                <p className="mt-3 text-xs text-subtle-foreground">
                  {t("bioCredit", { source: SOURCE_LABELS[bio.source] ?? bio.source })}
                  {bio.url && (
                    <>
                      {" · "}
                      <a href={bio.url} target="_blank" rel="noopener noreferrer" className="underline-offset-2 hover:underline">
                        {t("bioReadMore")}
                      </a>
                    </>
                  )}
                  {bio.source === "LASTFM" && <span> · CC BY-SA</span>}
                </p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">{t("noBio")}</p>
            )}

            {/* Facts: stats, tags, links */}
            <aside className="flex flex-col gap-5 self-start rounded-xl border border-border bg-elevated/40 p-5">
              {hasStats && (
                <dl className="grid grid-cols-2 gap-4">
                  {about.listeners !== null && (
                    <div>
                      <dt className="flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.14em] text-subtle-foreground uppercase">
                        <Headphones className="size-3.5" /> {t("listeners")}
                      </dt>
                      <dd className="mt-1 text-2xl font-bold tabular-nums">{formatCount(about.listeners)}</dd>
                    </div>
                  )}
                  {about.playcount !== null && (
                    <div>
                      <dt className="flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.14em] text-subtle-foreground uppercase">
                        <Play className="size-3.5" /> {t("plays")}
                      </dt>
                      <dd className="mt-1 text-2xl font-bold tabular-nums">{formatCount(about.playcount)}</dd>
                    </div>
                  )}
                </dl>
              )}

              {about.tags.length > 0 && (
                <div>
                  <h3 className="text-[11px] font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("tags")}</h3>
                  <ul className="mt-2 flex flex-wrap gap-1.5">
                    {about.tags.map((tag) => (
                      <li key={tag} className="rounded-full border border-border bg-background/60 px-2.5 py-1 text-xs font-medium text-foreground/85">
                        {tag}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {about.links.length > 0 && (
                <div>
                  <h3 className="text-[11px] font-semibold tracking-[0.14em] text-subtle-foreground uppercase">{t("links")}</h3>
                  <ul className="mt-2 flex flex-wrap gap-2">
                    {about.links.map((link) => {
                      const label = LINK_LABELS[link.type] ?? t("website");
                      return (
                        <li key={link.type}>
                          <a
                            href={link.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            aria-label={label}
                            title={label}
                            className="flex items-center gap-2 rounded-full border border-border bg-background/60 px-3 py-1.5 text-xs font-medium text-foreground/85 transition-colors hover:border-border-strong hover:bg-hover hover:text-foreground"
                          >
                            <ExternalLinkIcon type={link.type} className="size-3.5" />
                            {label}
                          </a>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              )}
            </aside>
          </div>
        </section>
      )}

      {similarInCatalog.length > 0 && (
        <HorizontalSection title={t("fansAlsoLike")}>
          {similarInCatalog.map(({ artist }) => (
            <MediaCard
              key={artist!.id}
              href={routes.artist(artist!.id)}
              title={artist!.name}
              subtitle={t("eyebrow")}
              artwork={artist!.artwork}
              seed={artist!.id}
              shape="circle"
            />
          ))}
        </HorizontalSection>
      )}

      {similarInCatalog.length === 0 && similarElsewhere.length > 0 && (
        <p className="text-sm text-muted-foreground">
          <span className="font-medium text-foreground/80">{t("fansAlsoLike")}:</span> {similarElsewhere.join(", ")}
        </p>
      )}
    </div>
  );
}
