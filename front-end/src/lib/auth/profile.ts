import "server-only";

import { db } from "@/lib/db";
import { pickUsername } from "./username";

/** One row of identity.user_profile — what Aura itself knows about an account. */
export interface Profile {
  userId: string;
  username: string;
  displayName: string;
  /** Uploaded photo URL, or null when the account has none. */
  avatarUrl: string | null;
  /** The sign-in provider's picture (Google), or null. */
  pictureUrl: string | null;
  createdAt: Date;
}

const COLUMNS = `user_id, username, display_name, avatar_url, picture_url, created_at`;

export async function findProfile(userId: string): Promise<Profile | null> {
  const { rows } = await db.query<ProfileRow>(`SELECT ${COLUMNS} FROM identity.user_profile WHERE user_id = $1`, [userId]);
  return rows[0] ? toProfile(rows[0]) : null;
}

export async function findProfileByUsername(username: string): Promise<Profile | null> {
  const { rows } = await db.query<ProfileRow>(`SELECT ${COLUMNS} FROM identity.user_profile WHERE lower(username) = lower($1)`, [
    username,
  ]);
  return rows[0] ? toProfile(rows[0]) : null;
}

/** Someone else already using this username? */
export async function usernameTaken(username: string, exceptUserId?: string): Promise<boolean> {
  const { rowCount } = await db.query(
    `SELECT 1 FROM identity.user_profile WHERE lower(username) = lower($1) AND ($2::uuid IS NULL OR user_id <> $2::uuid)`,
    [username, exceptUserId ?? null],
  );
  return (rowCount ?? 0) > 0;
}

/**
 * The profile for a signed-in account, created on first sight. Hints come from what sign-up sent
 * along in `user_metadata` (our register form: username + display_name; Google: name + picture);
 * a wanted username that is taken by then gets a numeric suffix rather than failing the login.
 */
export async function ensureProfile(hints: {
  userId: string;
  email?: string;
  username?: string;
  displayName?: string;
  picture?: string;
}): Promise<Profile> {
  const existing = await findProfile(hints.userId);
  if (existing) {
    // A changed Google photo is picked up on the next request; usually a no-op.
    if (hints.picture && hints.picture !== existing.pictureUrl) return updateProfile(hints.userId, { pictureUrl: hints.picture });
    return existing;
  }

  const displayName = (hints.displayName ?? hints.email?.split("@")[0] ?? "Listener").trim().slice(0, 60) || "Listener";
  const username = await pickUsername({ wanted: hints.username, email: hints.email, name: hints.displayName }, (c) => usernameTaken(c));
  try {
    const { rows } = await db.query<ProfileRow>(
      `INSERT INTO identity.user_profile (user_id, username, display_name, picture_url)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (user_id) DO UPDATE SET updated_at = identity.user_profile.updated_at
       RETURNING ${COLUMNS}`,
      [hints.userId, username, displayName, hints.picture ?? null],
    );
    return toProfile(rows[0]!);
  } catch (error) {
    // Lost a race on the username with a concurrent sign-up: pick again.
    if (isUniqueViolation(error)) return ensureProfile({ ...hints, username: undefined });
    throw error;
  }
}

export async function updateProfile(
  userId: string,
  patch: { displayName?: string; username?: string; avatarUrl?: string | null; pictureUrl?: string | null },
): Promise<Profile> {
  const { rows } = await db.query<ProfileRow>(
    `UPDATE identity.user_profile
        SET display_name = COALESCE($2, display_name),
            username     = COALESCE($3, username),
            avatar_url   = CASE WHEN $4::boolean THEN $5 ELSE avatar_url END,
            picture_url  = CASE WHEN $6::boolean THEN $7 ELSE picture_url END,
            updated_at   = now()
      WHERE user_id = $1
      RETURNING ${COLUMNS}`,
    [
      userId,
      patch.displayName ?? null,
      patch.username ?? null,
      patch.avatarUrl !== undefined,
      patch.avatarUrl ?? null,
      patch.pictureUrl !== undefined,
      patch.pictureUrl ?? null,
    ],
  );
  const row = rows[0];
  if (!row) throw new Error(`No profile for user ${userId}`);
  return toProfile(row);
}

export async function deleteProfile(userId: string): Promise<void> {
  await db.query(`DELETE FROM identity.user_profile WHERE user_id = $1`, [userId]);
}

export function isUniqueViolation(error: unknown): boolean {
  return typeof error === "object" && error !== null && (error as { code?: string }).code === "23505";
}

interface ProfileRow {
  user_id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  picture_url: string | null;
  created_at: Date;
}

function toProfile(row: ProfileRow): Profile {
  return {
    userId: row.user_id,
    username: row.username,
    displayName: row.display_name,
    avatarUrl: row.avatar_url,
    pictureUrl: row.picture_url,
    createdAt: row.created_at,
  };
}
