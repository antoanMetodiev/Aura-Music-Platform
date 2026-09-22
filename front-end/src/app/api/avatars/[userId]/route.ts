import { loadAvatar } from "@/lib/auth/avatars";

/** Serves an uploaded avatar. Public — the URL is on the profile, which friends see too. */
export async function GET(_request: Request, { params }: RouteContext<"/api/avatars/[userId]">) {
  const { userId } = await params;
  const avatar = await loadAvatar(decodeURIComponent(userId));
  if (!avatar) return new Response(null, { status: 404 });

  return new Response(new Uint8Array(avatar.bytes), {
    headers: {
      "Content-Type": avatar.contentType,
      // The URL carries `?v=<updatedAt>`, so it can be cached hard; a re-upload changes the URL.
      "Cache-Control": "public, max-age=31536000, immutable",
      "Last-Modified": avatar.updatedAt.toUTCString(),
    },
  });
}
