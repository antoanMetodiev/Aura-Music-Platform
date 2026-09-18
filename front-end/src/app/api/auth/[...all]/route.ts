import { toNextJsHandler } from "better-auth/next-js";
import { auth } from "@/lib/auth/auth";

/** Every Better Auth endpoint (sign-in, sign-up, OAuth callbacks, JWKS, token…) under /api/auth/*. */
export const { GET, POST } = toNextJsHandler(auth);
