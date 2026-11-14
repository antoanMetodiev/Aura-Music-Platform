/**
 * Applies db/identity/V*.sql in order to DATABASE_URL, recording each file in
 * identity.schema_migrations so a re-run is a no-op — the same idea as the services' Flyway.
 *
 *   npm run db:migrate
 */
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { Pool } from "pg";

const DIR = path.resolve("db/identity");

async function main() {
  const pool = new Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
  try {
    await pool.query(`CREATE SCHEMA IF NOT EXISTS identity`);
    await pool.query(
      `CREATE TABLE IF NOT EXISTS identity.schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())`,
    );
    const applied = new Set((await pool.query<{ name: string }>(`SELECT name FROM identity.schema_migrations`)).rows.map((r) => r.name));
    const files = (await readdir(DIR)).filter((f) => /^V\d+__.*\.sql$/.test(f)).sort(byVersion);

    let count = 0;
    for (const file of files) {
      if (applied.has(file)) continue;
      const sql = await readFile(path.join(DIR, file), "utf8");
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        await client.query(sql);
        await client.query(`INSERT INTO identity.schema_migrations (name) VALUES ($1)`, [file]);
        await client.query("COMMIT");
        console.log(`applied ${file}`);
        count++;
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
    }
    console.log(count ? `${count} migration(s) applied` : "identity schema is up to date");
  } finally {
    await pool.end();
  }
}

function byVersion(a: string, b: string): number {
  return Number(a.match(/^V(\d+)__/)?.[1]) - Number(b.match(/^V(\d+)__/)?.[1]);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
