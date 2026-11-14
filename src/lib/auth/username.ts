export const USERNAME_MIN = 3;
export const USERNAME_MAX = 30;
/** Lowercase letters, digits, dot and underscore — what fits in `/profile/[username]` unencoded. */
export const USERNAME_PATTERN = /^[a-z0-9](?:[a-z0-9._]*[a-z0-9])?$/;

/**
 * Picks a free username for a first-time user: the one they asked for (`wanted`), else the
 * email's local part, else their name — cleaned to the allowed alphabet, with a short numeric
 * suffix when taken. `taken` is asked against identity.user_profile, the source of truth.
 */
export async function pickUsername(
  hints: { wanted?: string; email?: string; name?: string },
  taken: (candidate: string) => Promise<boolean>,
): Promise<string> {
  const base = normalizeUsername(hints.wanted || hints.email?.split("@")[0] || hints.name || "listener");

  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = attempt === 0 ? base : `${base.slice(0, USERNAME_MAX - 5)}${randomDigits(4)}`;
    if (!(await taken(candidate))) return candidate;
  }
  return `${base.slice(0, USERNAME_MAX - 9)}${randomDigits(8)}`;
}

export function normalizeUsername(raw: string): string {
  let value = raw
    .normalize("NFKD")
    .toLowerCase()
    .replace(/[^a-z0-9._]+/g, "")
    .replace(/^[._]+|[._]+$/g, "")
    .slice(0, USERNAME_MAX);
  if (value.length < USERNAME_MIN) value = `${value}${randomDigits(USERNAME_MIN - value.length + 2)}`;
  return value;
}

function randomDigits(count: number): string {
  let out = "";
  for (let i = 0; i < count; i++) out += Math.floor(Math.random() * 10);
  return out;
}
