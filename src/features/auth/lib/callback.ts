import { safePath } from "@/lib/auth/redirects";

/**
 * Absolute URL of our auth callback with the same-site path to land on afterwards. Every flow
 * that leaves the site (Google, confirmation and reset emails) comes back through it.
 */
export function callbackUrl(next: string): string {
  return `${window.location.origin}/api/auth/callback?next=${encodeURIComponent(safePath(next))}`;
}
