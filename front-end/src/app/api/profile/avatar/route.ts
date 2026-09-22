import { AVATAR_MAX_UPLOAD_BYTES, deleteAvatar, saveAvatar } from "@/lib/auth/avatars";
import { updateProfile } from "@/lib/auth/profile";
import { getSession } from "@/lib/auth/session";

const ACCEPTED = new Set(["image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"]);

/**
 * Upload a new profile photo (multipart `file`). The bytes go to identity.user_avatar, the URL to
 * identity.user_profile.avatar_url, and it wins over the Google picture from then on.
 */
export async function POST(request: Request) {
  const session = await getSession();
  if (!session) return Response.json({ code: "UNAUTHORIZED" }, { status: 401 });
  const { user } = session;

  const form = await request.formData().catch(() => null);
  const file = form?.get("file");
  if (!(file instanceof File)) return Response.json({ code: "NO_FILE" }, { status: 400 });
  if (!ACCEPTED.has(file.type)) return Response.json({ code: "UNSUPPORTED_TYPE" }, { status: 415 });
  if (file.size > AVATAR_MAX_UPLOAD_BYTES) return Response.json({ code: "TOO_LARGE" }, { status: 413 });

  let image: string;
  try {
    image = await saveAvatar(user.sub, await file.arrayBuffer());
  } catch {
    return Response.json({ code: "UNREADABLE_IMAGE" }, { status: 422 });
  }
  await updateProfile(user.sub, { avatarUrl: image });
  return Response.json({ image });
}

/** Remove the uploaded photo; the avatar falls back to the Google picture or initials. */
export async function DELETE() {
  const session = await getSession();
  if (!session) return Response.json({ code: "UNAUTHORIZED" }, { status: 401 });
  const { user } = session;

  await deleteAvatar(user.sub);
  await updateProfile(user.sub, { avatarUrl: null });
  return Response.json({ image: user.picture ?? null });
}
