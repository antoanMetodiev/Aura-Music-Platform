import "server-only";

import { Client, type QueryResult, type QueryResultRow } from "pg";
import { getCloudflareContext } from "@opennextjs/cloudflare";

/**
 * The web app's own Postgres connection — the same Supabase project as the services, used only for
 * the `identity` schema (what Supabase Auth doesn't hold for us: profiles and uploaded avatars).
 *
 * One client per query, not a shared pool. On Workers a connection opened while serving one request
 * cannot be used by the next one ("Cannot perform I/O on behalf of a different request"), so a
 * module-level `Pool` — which is what this was while the app only ran on Node — would break the
 * moment a second request reused the isolate. Hyperdrive is what makes that cheap: it keeps the
 * warm pool next to Supabase, and the Worker's "connection" is to Hyperdrive at the edge. Without
 * the binding this still works, it just pays a real Postgres handshake per query.
 */
export const db = {
  async query<T extends QueryResultRow = QueryResultRow>(text: string, values?: unknown[]): Promise<QueryResult<T>> {
    const client = new Client({ connectionString: connectionString() });
    await client.connect();
    try {
      return await client.query<T>(text, values);
    } finally {
      // Never let a failed close mask the query's own error.
      await client.end().catch(() => {});
    }
  },
};

/**
 * Hyperdrive when it is bound (deployed, and in `next dev` via initOpenNextCloudflareForDev), else
 * the DATABASE_URL secret. Reading the binding lazily keeps this importable from scripts that run
 * outside the Workers runtime, such as `npm run db:migrate`.
 */
function connectionString(): string {
  let hyperdrive: string | undefined;
  try {
    hyperdrive = getCloudflareContext().env.HYPERDRIVE?.connectionString;
  } catch {
    // Not inside a Workers request context (a plain Node script) — fall through to the env var.
  }
  const url = hyperdrive ?? process.env.DATABASE_URL;
  if (!url) throw new Error("Missing DATABASE_URL (or a HYPERDRIVE binding)");
  return url;
}
