import { createAuthClient } from "better-auth/react";
import { inferAdditionalFields, jwtClient, usernameClient } from "better-auth/client/plugins";
import type { auth } from "./auth";

/**
 * Browser-side Better Auth client. Talks to our own `/api/auth/*` route handler, so it needs no
 * base URL in the same-origin case. `useSession()` here is for client components that need the
 * live session (e.g. the account menu); server components use `getSession()` instead.
 */
export const authClient = createAuthClient({
  plugins: [usernameClient(), jwtClient(), inferAdditionalFields<typeof auth>()],
});

export const { useSession, signIn, signUp, signOut } = authClient;
