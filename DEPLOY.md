# Деплой на front-end-а в Cloudflare Workers

Next.js 16 върви на Workers през **OpenNext** (`@opennextjs/cloudflare`): `next build` остава
непроменен, адаптерът превръща изхода му в Worker в `.open-next/`. `npm run dev` не е засегнат.

Backend-ът не е тук — Spring services-ите стоят на Render зад API gateway-а (Project-Info.md §4).

---

## 1. Еднократна подготовка на Cloudflare акаунта

```bash
npx wrangler login
```

### Secrets (server-only, никога в repo-то)

```bash
cd front-end
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY   # Supabase → Project Settings → API Keys
npx wrangler secret put DATABASE_URL                # Supavisor pooler URL (порт 6543)
```

`NEXT_PUBLIC_*` НЕ са secrets — влизат в бъндъла при build от `.env.production`, който е в repo-то.

### Images

Аватарите се смаляват от Images binding-а (`sharp` е native binary и не работи на Workers).
Включи **Images** в Cloudflare dashboard → Images. Binding-ът вече е в `wrangler.jsonc`.

### Hyperdrive (препоръчително)

Всяка заявка на влязъл потребител чете профилния ред от Postgres, т.е. базата е на горещия път.
Без Hyperdrive всеки isolate прави пълен handshake до Supabase:

```bash
npx wrangler hyperdrive create aura-identity --connection-string="<DATABASE_URL>"
```

После разкоментирай реда `"hyperdrive"` в `wrangler.jsonc` с върнатото id и пусни `npm run cf-typegen`.
Без него приложението пак работи — `src/lib/db.ts` пада обратно към `DATABASE_URL`.

---

## 2. Преди първия деплой

1. В `.env.production` смени `NEXT_PUBLIC_APP_URL` с реалния домейн и `NEXT_PUBLIC_API_BASE_URL`
   с адреса на gateway-а в Render. От `APP_URL` се строят линковете в имейлите и OAuth callback-ът.
2. В Supabase dashboard → Authentication → URL Configuration добави продукционните:
   - Site URL: `https://<домейн>`
   - Redirect URL: `https://<домейн>/api/auth/callback`
3. Ако gateway-ът е на друг домейн, добави го в CORS-а му (`CORS_ALLOWED_ORIGINS`).

---

## 3. Деплой

**През CI (препоръчително)** — `.github/workflows/deploy-front-end.yml` се пуска при push в `main`,
който променя `front-end/`. Иска два GitHub secrets: `CLOUDFLARE_API_TOKEN` (Workers Scripts: Edit)
и `CLOUDFLARE_ACCOUNT_ID`.

**Ръчно:**

```bash
npm run preview   # Worker-ът локално през wrangler, чете .dev.vars
npm run deploy    # build + upload
```

> **Windows:** локален build иска включен Developer Mode (Settings → System → For developers →
> Developer Mode). Без него OpenNext гърми с `EPERM: operation not permitted, symlink`, защото
> symlink-ва traced node_modules. CI-ят е на Linux и няма този проблем.

---

## 4. Какво къде живее

| Стойност | Къде | Защо |
|---|---|---|
| `NEXT_PUBLIC_*` | `.env.production` (в repo-то) | build-time, публични по дефиниция |
| `SUPABASE_SERVICE_ROLE_KEY`, `DATABASE_URL` | `wrangler secret put` | runtime, никога в бъндъла |
| Същите за `npm run preview` | `.dev.vars` (gitignored) | локалният Worker ги чете оттам |
| TIDAL / YouTube / Last.fm ключове | никъде тук | те са на backend services-ите (Project-Info.md §8) |

## 5. Ограничения, които следват от Workers

- **Без `sharp`** — аватарите минават през `env.IMAGES` (`src/lib/auth/avatars.ts`). Същият резултат:
  256×256, cover, WebP q82.
- **Без споделен connection pool** — връзка, отворена в една заявка, не може да се ползва в следващата
  („Cannot perform I/O on behalf of a different request“), затова `src/lib/db.ts` прави клиент на
  заявка. Hyperdrive е това, което прави цената поносима.
- **Без `export const runtime = "edge"`** — OpenNext иска Node runtime-а; в кода няма такива.
