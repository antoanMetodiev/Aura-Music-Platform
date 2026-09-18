/**
 * Applies Better Auth's schema for the current `auth.ts` config to DATABASE_URL — the
 * `identity` schema plus the user/session/account/verification/jwks tables. Idempotent: computes the diff
 * against the live database and applies only what's missing. Also writes the SQL it would run
 * to db/auth/, so the schema stays reviewable in git like the services' Flyway files.
 *
 *   npm run auth:migrate            # apply
 *   npm run auth:migrate -- --plan  # only write the SQL, touch nothing
 */
import { writeFile } from "node:fs/promises";
import { getMigrations } from "better-auth/db/migration";

async function main() {
  const planOnly = process.argv.includes("--plan");
  const { auth } = await import("../src/lib/auth/auth");
  const migrations = await getMigrations(auth.options);

  const sql = await migrations.compileMigrations();
  const created = migrations.toBeCreated.map((t) => t.table);
  const added = migrations.toBeAdded.map((t) => `${t.table}(${Object.keys(t.fields).join(", ")})`);
  if (migrations.schemaProblems.length) console.warn("schema problems:", migrations.schemaProblems);

  // An up-to-date database still compiles to a lone ";".
  if (!sql.replace(/[;\s]/g, "")) {
    console.log("auth schema is up to date");
    return;
  }
  const stamp = new Date().toISOString().slice(0, 10).replaceAll("-", "");
  const file = `db/auth/${stamp}_auth_schema.sql`;
  await writeFile(file, sql);
  console.log(`wrote ${file}\n  create: ${created.join(", ") || "-"}\n  add:    ${added.join(", ") || "-"}`);
  if (!planOnly) {
    await migrations.runMigrations();
    console.log("applied");
  }
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
