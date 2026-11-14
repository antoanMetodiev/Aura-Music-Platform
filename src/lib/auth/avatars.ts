import "server-only";

import { getCloudflareContext } from "@opennextjs/cloudflare";
import { db } from "@/lib/db";

/** Every avatar is normalised to this: square WebP, a few KB. */
export const AVATAR_SIZE = 256;
export const AVATAR_MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
const CONTENT_TYPE = "image/webp";

/** Public URL for a user's uploaded avatar; `v` busts caches after a re-upload. */
export function avatarUrl(userId: string, updatedAt: Date): string {
  return `/api/avatars/${encodeURIComponent(userId)}?v=${updatedAt.getTime()}`;
}

/** Resizes + re-encodes the upload and stores it in identity.user_avatar; returns the public URL. */
export async function saveAvatar(userId: string, upload: ArrayBuffer): Promise<string> {
  const bytes = await normalise(upload);

  const { rows } = await db.query<{ updated_at: Date }>(
    `INSERT INTO identity.user_avatar (user_id, content_type, data, updated_at)
     VALUES ($1, $2, $3, now())
     ON CONFLICT (user_id) DO UPDATE SET content_type = EXCLUDED.content_type, data = EXCLUDED.data, updated_at = now()
     RETURNING updated_at`,
    [userId, CONTENT_TYPE, bytes],
  );
  return avatarUrl(userId, rows[0]!.updated_at);
}

/**
 * Square WebP at {@link AVATAR_SIZE}, cropped to the interesting part. This used to be `sharp`,
 * which is a native binary and cannot run on Workers; the Images binding does the same work in the
 * runtime itself. It also settles what the upload actually is — a file the browser labelled
 * `image/png` but isn't fails here rather than being served back to everyone as a broken avatar.
 */
async function normalise(upload: ArrayBuffer): Promise<Buffer> {
  const { env } = getCloudflareContext();
  if (!env.IMAGES) {
    throw new Error("No IMAGES binding — add `\"images\": { \"binding\": \"IMAGES\" }` to wrangler.jsonc");
  }
  const result = await env.IMAGES.input(streamOf(upload))
    // gravity "auto" keeps the subject in frame when a rectangular photo is cropped square.
    .transform({ width: AVATAR_SIZE, height: AVATAR_SIZE, fit: "cover", gravity: "auto" })
    .output({ format: CONTENT_TYPE, quality: 82 });
  return Buffer.from(await result.response().arrayBuffer());
}

/** The binding takes the bytes as a stream. */
function streamOf(buffer: ArrayBuffer): ReadableStream<Uint8Array> {
  return new Blob([buffer]).stream() as ReadableStream<Uint8Array>;
}

export async function loadAvatar(userId: string): Promise<{ contentType: string; bytes: Buffer; updatedAt: Date } | null> {
  const { rows } = await db.query<{ content_type: string; data: Buffer; updated_at: Date }>(
    `SELECT content_type, data, updated_at FROM identity.user_avatar WHERE user_id = $1`,
    [userId],
  );
  const row = rows[0];
  return row ? { contentType: row.content_type, bytes: row.data, updatedAt: row.updated_at } : null;
}

export async function deleteAvatar(userId: string): Promise<void> {
  await db.query(`DELETE FROM identity.user_avatar WHERE user_id = $1`, [userId]);
}
