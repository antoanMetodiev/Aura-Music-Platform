import type { GenericEndpointContext } from "@better-auth/core";

export const USERNAME_MIN = 3;
export const USERNAME_MAX = 30;
/** Lowercase letters, digits, dot and underscore — what fits in `/profile/[username]` unencoded. */
export const USERNAME_PATTERN = /^[a-z0-9](?:[a-z0-9._]*[a-z0-9])?$/;

/**
 * Picks a free username for an account that arrived without one (Google sign-up): the email's
 * local part, cleaned to the allowed alphabet, with a short numeric suffix when taken.
 */
export async function ensureUsername(
  user: { email?: string; name?: string },
  ctx: GenericEndpointContext | null,
): Promise<string> {
  const base = normalizeUsername(user.email?.split("@")[0] ?? user.name ?? "listener");

  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = attempt === 0 ? base : `${base.slice(0, USERNAME_MAX - 5)}${randomDigits(4)}`;
    if (!(await usernameTaken(candidate, ctx))) return candidate;
  }
  return `${base.slice(0, USERNAME_MAX - 9)}${randomDigits(8)}`;
}

function normalizeUsername(raw: string): string {
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

async function usernameTaken(candidate: string, ctx: GenericEndpointContext | null): Promise<boolean> {
  if (!ctx) return false;
  const existing = await ctx.context.adapter.findOne({
    model: "user",
    where: [{ field: "username", value: candidate }],
  });
  return existing !== null;
}
