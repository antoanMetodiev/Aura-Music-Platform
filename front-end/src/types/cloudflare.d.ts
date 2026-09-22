/**
 * Bindings this app reads that are not (yet) in wrangler.jsonc, so `wrangler types` cannot know
 * about them. Merged into the generated `CloudflareEnv` in cloudflare-env.d.ts.
 */
interface CloudflareEnv {
  /**
   * Postgres connection pool in front of Supabase (the `identity` schema). Optional: without the
   * binding `src/lib/db.ts` connects straight to DATABASE_URL. See wrangler.jsonc for how to
   * create it.
   */
  HYPERDRIVE?: Hyperdrive;
}
