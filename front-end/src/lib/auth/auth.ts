import "server-only";

import { betterAuth } from "better-auth";
import { nextCookies } from "better-auth/next-js";
import { jwt, username } from "better-auth/plugins";
import { PostgresDialect } from "kysely";
import { Pool } from "pg";
import { sendPasswordResetEmail, sendVerificationEmail } from "./mailer";
import { ensureUsername, USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN } from "./username";

/**
 * Better Auth — Aura's identity provider (Project-Info.md §8, ADR-011).
 *
 * Lives inside Next.js, owns the `identity` schema in the shared Postgres and issues two things:
 *   - an httpOnly session cookie for the web UI (read by `getSession()` in server components);
 *   - short-lived JWTs (RS256, `/api/auth/jwks`) that the Spring services validate offline
 *     with `spring-boot-starter-oauth2-resource-server`. The user id (`sub`) is the canonical
 *     user UUID across the whole platform.
 *
 * Never import this from a client component — it holds the database pool and provider secrets.
 */
export const auth = betterAuth({
  appName: "Aura",
  baseURL: process.env.NEXT_PUBLIC_APP_URL,
  secret: process.env.BETTER_AUTH_SECRET,

  database: {
    dialect: new PostgresDialect({
      // Same Supabase project as the services, through the Supavisor pooler (transaction mode),
      // never the direct connection. Small pool on purpose: the whole platform shares ~60 slots.
      pool: new Pool({ connectionString: process.env.DATABASE_URL, max: 4 }),
    }),
    type: "postgres",
    // Own schema, like every service (Project-Info.md §6) — qualifies every statement, so we
    // don't depend on the pooler's search_path. Not `auth`: Supabase reserves that one for its
    // own (unused by us) GoTrue tables and the `postgres` role can't create there.
    schemaName: "identity",
  },
  advanced: {
    // Postgres generates the id (gen_random_uuid()) — the services store it as `uuid`.
    database: { generateId: "uuid" },
  },

  emailAndPassword: {
    enabled: true,
    minPasswordLength: 8,
    maxPasswordLength: 128,
    // MVP: sign in right after sign-up; the verification mail still goes out and the flag lands on
    // the user. Flip to `true` once Resend is wired and we want to gate on it.
    requireEmailVerification: false,
    sendResetPassword: async ({ user, url }) => {
      await sendPasswordResetEmail(user.email, url);
    },
  },
  emailVerification: {
    sendOnSignUp: true,
    autoSignInAfterVerification: true,
    sendVerificationEmail: async ({ user, url }) => {
      await sendVerificationEmail(user.email, url);
    },
  },

  socialProviders: {
    google: {
      clientId: process.env.GOOGLE_CLIENT_ID ?? "",
      clientSecret: process.env.GOOGLE_CLIENT_SECRET ?? "",
      // Always let the person pick an account — otherwise Google silently reuses the last one.
      prompt: "select_account",
    },
  },

  session: {
    expiresIn: 60 * 60 * 24 * 30, // 30 days
    updateAge: 60 * 60 * 24, // refresh the expiry at most once a day
    cookieCache: {
      enabled: true,
      // Server components read the session on every request; a 5-minute signed cookie cache keeps
      // that off the database. Sign-out and password changes still invalidate immediately.
      maxAge: 60 * 5,
    },
  },

  databaseHooks: {
    user: {
      create: {
        // Google sign-ups arrive without a username; every Aura user needs one (`/profile/[username]`).
        before: async (user, ctx) => {
          if (typeof user.username === "string" && user.username.length > 0) return;
          const username = await ensureUsername(user, ctx);
          return { data: { ...user, username, displayUsername: username } };
        },
      },
    },
  },

  plugins: [
    username({
      minUsernameLength: USERNAME_MIN,
      maxUsernameLength: USERNAME_MAX,
      usernameValidator: (value) => USERNAME_PATTERN.test(value),
    }),
    jwt({
      jwks: {
        // RS256 rather than the EdDSA default: what Spring Security / Nimbus verifies without any
        // extra configuration on the resource-server side.
        keyPairConfig: { alg: "RS256" },
      },
      jwt: {
        issuer: process.env.NEXT_PUBLIC_APP_URL,
        audience: "aura-api",
        expirationTime: "15m",
        definePayload: ({ user }) => ({
          email: user.email,
          username: user.username,
          name: user.name,
        }),
      },
    }),
    // Must stay last: lets Better Auth set cookies from server actions / server components.
    nextCookies(),
  ],
});

export type Session = typeof auth.$Infer.Session;
export type AuthUser = Session["user"];
