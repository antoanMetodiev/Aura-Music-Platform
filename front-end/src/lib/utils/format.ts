/** 214000 → "3:34"; 3725000 → "1:02:05" */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = hours > 0 ? String(minutes).padStart(2, "0") : String(minutes);
  const ss = String(seconds).padStart(2, "0");
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** 43m 50s style total for playlists/albums. */
export function formatTotalDuration(ms: number): string {
  const totalMinutes = Math.floor(ms / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours} hr ${minutes} min` : `${minutes} min`;
}

/** 1234567 → "1.2M", 45678 → "45.7K" */
export function formatCount(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)}B`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

export type RelativeTimeKey = "justNow" | "minutes" | "hours" | "days" | "weeks";

/**
 * Compact relative time as translation parts: {key: "minutes", n: 3} → t(`time.${key}`, {n}).
 * Kept locale-agnostic so both server and client components can translate it.
 */
export function relativeTimeParts(iso: string, now: Date = new Date()): { key: RelativeTimeKey; n: number } {
  const diffSeconds = Math.round((now.getTime() - new Date(iso).getTime()) / 1000);
  if (diffSeconds < 45) return { key: "justNow", n: 0 };
  const minutes = Math.round(diffSeconds / 60);
  if (minutes < 60) return { key: "minutes", n: minutes };
  const hours = Math.round(minutes / 60);
  if (hours < 24) return { key: "hours", n: hours };
  const days = Math.round(hours / 24);
  if (days < 7) return { key: "days", n: days };
  return { key: "weeks", n: Math.round(days / 7) };
}

export type GreetingKey = "night" | "morning" | "afternoon" | "evening";

export function getGreetingKey(date: Date = new Date()): GreetingKey {
  const hour = date.getHours();
  if (hour < 5) return "night";
  if (hour < 12) return "morning";
  if (hour < 18) return "afternoon";
  return "evening";
}

export function joinArtists(artists: { name: string }[]): string {
  return artists.map((a) => a.name).join(", ");
}
