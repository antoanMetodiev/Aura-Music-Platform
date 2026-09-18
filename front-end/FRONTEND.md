# AURA — Frontend Blueprint

Този документ описва какво научихме от изследването на Spotify Web Player и TIDAL Web,
и дефинира страниците, layout-а, компонентите, структурата на кода и конвенциите,
които следваме при писането на frontend-а на Aura.

Той е **source of truth** за frontend решенията. Спецификацията на цялата система е в
`../Project-Info.md`.

---

## 1. Какво научихме от Spotify и TIDAL

### 1.1 Spotify Web Player (open.spotify.com)

**Глобален layout (desktop, 3 колони + player):**

```
┌───────────────────────────────────────────────────────────────────────────┐
│ TOP BAR: logo | Home btn | [ Search input (global) ] | notif | friends | 👤 │
├──────────────┬────────────────────────────────────────────┬───────────────┤
│ LEFT SIDEBAR │ MAIN CONTENT (scrollable, rounded panel)   │ RIGHT PANEL   │
│ "Your        │                                            │ Now Playing / │
│  Library"    │  Filter chips: All | Music | Podcasts       │ Queue /       │
│  + Create    │  Quick-access grid (recent 2–8 items)       │ Friend        │
│  chips:      │  Section: "Made for X" → horizontal cards   │ Activity      │
│  Playlists / │  Section: "Recently played"                 │ (collapsible) │
│  Artists /   │  Section: "Your top mixes"                  │               │
│  Albums      │  Section: "Popular radio"                   │               │
│  search+sort │  Section: "More like <artist>"              │               │
│  list of     │  Section: "Recommended for today"           │               │
│  library     │  Section: "Based on your recent listening"  │               │
│  items       │  Footer (links)                             │               │
├──────────────┴────────────────────────────────────────────┴───────────────┤
│ PLAYER BAR: [art] title/artist ♥ | ⇄ ⏮ ⏯ ⏭ ↻ + progress | lyrics queue 🔊 ⛶ │
└───────────────────────────────────────────────────────────────────────────┘
```

Ключови наблюдения:

- **Три отделни панела** (sidebar, main, right panel) — всеки е независимо скролируем,
  с заоблени ъгли и тъмен фон (#121212), разделени от почти черен background (#000).
- **Player bar** е винаги закачен долу и е на пълна ширина под трите панела.
- **Global search** е в top bar-а, не в sidebar-а. Пише се и резултатите се показват
  на `/search/[query]` с chips за филтриране (All / Songs / Artists / Playlists /
  Albums / Profiles / Podcasts / Genres).
- **Search без query** показва "Browse all" grid с цветни жанрови карти.
- **Search с query** показва: "Top result" карта (голяма) + "Songs" списък (4–5 реда)
  отгоре, после хоризонтални секции "Featuring X", "Artists", "Albums", "Playlists".
- **Home** е изцяло съставен от хоризонтално скролируеми секции с квадратни карти
  (artwork + title + subtitle). Секцията има заглавие (link) + "Show all" вдясно.
- **Artist page**: full-bleed hero (голямо изображение, gradient към черно), verified badge,
  "N monthly listeners", action row (Play, Shuffle, Follow, ...), "Popular" (top 5 с
  play count + duration, "See more"), "Discography" с tabs (Popular releases / Albums /
  Singles & EPs), "Featuring X", "Fans also like", "Appears on", "About" (bio + rank).
- **Album page**: hero с artwork вляво (≈232px), тип ("Album"), голямо заглавие, artist
  avatar + name • year • N songs, duration. Action row (Play, Shuffle, Add, Download,
  More). Track table: `# | Title (+artist) | ♥ | duration`. Footer: release date,
  copyright. "More by <artist>" секция.
- **Playlist / Liked songs**: същият hero модел но с gradient background (Liked songs има
  фиксиран purple→blue gradient и ♥ икона). Track table има повече колони:
  `# | Title | Album | Date added | ♥ | duration`.
- **Track page**: hero (artwork, "Song", title, artist • album • year • duration • plays),
  action row, Lyrics блок, "Artist" карта, "Recommended based on this song",
  "Popular tracks by artist", "From the album".
- Track table row при hover: номерът става ▶ бутон, показва се ♥ и "…" меню.
- **Hero background** динамично взима доминантния цвят от artwork-а и го преливa към
  фона (dominant-color gradient).
- **Sidebar library** има filter chips, search в библиотеката, sort (Recents / Recently
  added / Alphabetical), и всеки item е `[art] name / type • owner`.
- Compact mode: sidebar се свива до само икони.

### 1.2 TIDAL Web (tidal.com)

**Глобален layout:**

```
┌────────────────────────────────────────────────────────────────────────────┐
│ SIDEBAR (fixed)   │ MAIN CONTENT                       │ QUEUE PANEL       │
│  logo   [collapse]│  ← → history buttons               │ "Play queue"      │
│  ♫ Music          │  Tabs: For You | Staff Picks | ...  │ Playing from: X   │
│  ⌕ Search         │  Section (title + ‹ › arrows +      │ [now playing row] │
│  ⚲ Page           │           "View all")               │ Next up: list     │
│  ↑ Upload         │   → horizontal grid of cards         │ (with ✕ remove)   │
│  ▤ Collection  ›  │  Section ...                        │                   │
│  ─────────────    │                                     │ [search in top    │
│  Playlists  + ⇅   │                                     │  right corner]    │
│  (list)           │                                     │                   │
├───────────────────┴─────────────────────────────────────┴───────────────────┤
│ PLAYER: [art] title/artist ♥ … | ⇄ ⏮ ⏯ ⏭ ↻ + progress | queue 🔊 [QUALITY]  │
└────────────────────────────────────────────────────────────────────────────┘
```

Ключови наблюдения:

- **По-строг, по-технически, по-минималистичен** от Spotify. Чисто черен фон, по-малко
  цветове, серифни/тесни заглавия, повече whitespace. Това е по-близо до желаната
  визуална идентичност на Aura.
- **Sidebar навигация** е фиксирана вляво с текст + икона; секция "Playlists" под нея.
- **Search** е горе вдясно (малко поле) — по-дискретно от Spotify.
- **Секциите** имат `‹ ›` стрелки за хоризонтален скрол до заглавието (не само swipe).
- **Card**: квадратен artwork, заглавие, подзаглавие; малки badges (explicit, hi-res)
  до заглавието.
- **Artist page**: hero с голямо background изображение и **sticky compact header** при
  скрол (малък кръгъл avatar + име + action бутони остават залепени горе). Action row:
  Play, Shuffle, Follow, Artist radio, Share, More — всеки с **икона + етикет отдолу**.
  Секции: Top tracks (таблица `TITLE | ARTIST | ALBUM | TIME`), Albums, EP & Singles,
  Playlists, Appears on, Credits, Similar artists.
- **Queue panel** вдясно: "Now playing" + "Next up" с бутони за премахване и
  "Clear" — постоянен, не се крие зад Now Playing view.
- **Player** показва quality badge (LOW / HIGH / MAX) — при нас ще стане
  **provider/source badge** или ще го пропуснем.
- Search results: "Top result" + отделни колони/секции по тип.

### 1.3 Изводи за Aura

| Област               | Взимаме от Spotify                                     | Взимаме от TIDAL                                        |
| -------------------- | ------------------------------------------------------ | ------------------------------------------------------- |
| Layout               | 3-panel + full-width player, resizable/collapsible     | Фиксиран sidebar с ясна навигация + playlists под нея  |
| Визия                | Информационна плътност, hover states, dominant colors  | Тъмен, строг, минимален, премиум; повече whitespace     |
| Home                 | Много персонализирани секции, quick-access grid        | Tabs горе (For you / Friends / New), стрелки на секции  |
| Search               | Chips за филтриране, Top result + Songs list           | Дискретно поле, но при нас е в top bar (Spotify модел)  |
| Artist               | Popular с play count, Discography tabs, About          | Sticky compact header, action row с етикети             |
| Album / Playlist     | Hero + track table модел, "More by artist"             | Чист table header `TITLE / ARTIST / ALBUM / TIME`       |
| Player               | Пълен набор от контроли, lyrics/queue/device бутони     | Queue като постоянен десен панел                        |
| Social (Aura-only)   | Friend activity панел (desktop app)                    | —                                                       |

**Aura-specific**: десният панел ще бъде **tabbed**: `Now Playing | Queue | Friends`.
"Friends" показва real-time activity ("Иван слуша Radiohead — Nude") — това е нашата
ключова social диференциация и трябва да е винаги на един клик разстояние.

---

## 2. Визуална идентичност (design tokens)

Следва `Project-Info.md §45`. Тъмен, premium, сериозен, минимален.

```
Background
  --bg-base        #070A12   near-black navy  (page background)
  --bg-panel       #0D1220   sidebar / main / right panel
  --bg-elevated    #141B2E   cards, hover rows, popovers
  --bg-hover       #1B2439   hover state на карти/редове
  --bg-active      #22304D   selected / active

Gradient (hero, active player, highlights)
  --gradient-hero  linear-gradient(180deg, #0B2A5B 0%, #0A1A3C 45%, #070A12 100%)
  deep navy → dark rich blue → black

Text
  --text-primary   #F5F7FA   white
  --text-secondary #9AA6BF   cool gray
  --text-muted     #5F6B85

Border
  --border-subtle  #1C2438
  --border-strong  #2A3550

Accent
  --accent         #2F6BFF   deep blue (buttons, active nav, progress bar)
  --accent-hover   #4C82FF
  --accent-muted   #1E3F8F
  --danger         #E5484D
  --success        #3DD68C  (само за малки индикатори — НЕ Spotify green за branding)

Radius
  --radius-sm 6px   --radius-md 10px   --radius-lg 14px   (без "pill" навсякъде)

Typography
  Display/headings: "Geist" или "Inter Tight" — тесен, технически
  Body: "Inter" / "Geist"
  Mono (durations, counters): "Geist Mono"
```

Забранено: Spotify green като бранд цвят, TIDAL визуална идентичност, прекомерно purple,
neon/cyberpunk, glassmorphism навсякъде, прекалено заоблени елементи.

Dominant-color gradient на hero (както Spotify) е **позволен**, но се смесва с navy
базата, а не заменя палитрата.

---

## 3. Глобален layout на Aura

### Desktop (≥ 1024px)

```
┌───────────────────────────────────────────────────────────────────────────┐
│ TOP BAR (h-16)                                                            │
│  [◀ ▶]        [ ⌕  Search tracks, artists, albums, friends…   ]  🔔  👤   │
├──────────────┬────────────────────────────────────────────┬───────────────┤
│ SIDEBAR      │ MAIN (scroll)                              │ RIGHT PANEL   │
│ w-64 (→w-16) │                                            │ w-80 (toggle) │
│              │                                            │               │
│ AURA logo    │                                            │ tabs:         │
│ ⌂ Home       │                                            │ Now Playing   │
│ ⌕ Search     │                                            │ Queue         │
│ ▤ Library    │                                            │ Friends       │
│ ♥ Liked      │                                            │               │
│ ◷ Recent     │                                            │               │
│ ─────────    │                                            │               │
│ ☺ Friends    │                                            │               │
│ 🔔 Notifs    │                                            │               │
│ ─────────    │                                            │               │
│ Playlists +  │                                            │               │
│  · list      │                                            │               │
├──────────────┴────────────────────────────────────────────┴───────────────┤
│ PLAYER BAR (h-20)                                                         │
│ [art] Title / Artist ♥ │  ⇄ ⏮ ⏯ ⏭ ↻   0:42 ━━━━━━──── 3:30  │ ≡ ☺ 🔊 ━━ ⛶ │
└───────────────────────────────────────────────────────────────────────────┘
```

### Tablet (768–1023px)

- Sidebar е collapsed (само икони), може да се разгъне като overlay.
- Right panel е скрит; отваря се като drawer от бутоните в player-а.

### Mobile (< 768px)

```
┌────────────────────────┐
│ MAIN (scroll)          │
│                        │
│                        │
├────────────────────────┤
│ MINI PLAYER (h-14)     │  ← tap → full-screen player (sheet)
│ [art] Title · Artist ⏯ │
├────────────────────────┤
│ BOTTOM NAV (h-16)      │
│ ⌂ Home ⌕ Search ▤ Lib  │
│ ☺ Friends 👤 Profile   │
└────────────────────────┘
```

Full-screen player (mobile): голям artwork, title/artist, ♥, progress, контроли,
бутони за queue / lyrics / share, drag-down за затваряне.

---

## 4. Страници (App Router)

Route groups: `(auth)` — без app shell; `(app)` — с пълния shell (sidebar + player).

| Route                        | Page                | Основни блокове                                                                                                                                       |
| ---------------------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/login`                     | Login               | Centered card, email+password, OAuth (Google), link към register/forgot                                                                              |
| `/register`                  | Register            | email, password, username, display name; Zod validation                                                                                              |
| `/forgot-password`           | Forgot password     | email → Better Auth reset link (Resend; в dev линкът се печата в конзолата)                                                                                                                                |
| `/reset-password`            | Reset password      | Каца от линка в имейла (`?token=`): нова парола + потвърждение                                                                                       |
| `/home`                      | Home                | Greeting header ("Good morning, Antoan"), QuickAccessGrid, секции: Recently played, Made for you, Friends are listening to, Trending among friends, New releases, Because you listened to…, Recommended playlists |
| `/search`                    | Search (empty)      | Recent searches, Browse genres grid                                                                                                                   |
| `/search/[query]`            | Search results      | FilterChips (All / Tracks / Artists / Albums / Playlists / People), TopResultCard + TrackList (top 5), секции по тип                                  |
| `/artists/[artistId]`        | Artist              | ArtistHero (full-bleed + sticky compact header), ActionRow, PopularTracks (top 5/10), Discography (tabs), Appears on, Fans also like, About          |
| `/albums/[albumId]`          | Album               | MediaHero (artwork left), ActionRow, TrackTable (# / Title / ♥ / duration), Release info, More by artist                                              |
| `/tracks/[trackId]`          | Track               | MediaHero, ActionRow, ArtistCard, "Recommended based on this track", Popular by artist, From the album                                              |
| `/playlists/[playlistId]`    | Playlist            | MediaHero (artwork/mosaic), owner avatar, ActionRow (Play, Shuffle, Save, Share, …), TrackTable (# / Title / Album / Date added / ♥ / duration), Recommended tracks to add |
| `/library`                   | Library             | FilterChips (Playlists / Artists / Albums), sort, grid/list toggle, MediaCardGrid                                                                       |
| `/liked`                     | Liked songs         | MediaHero (fixed gradient + ♥), TrackTable                                                                                                             |
| `/recently-played`           | Recently played     | Grouped by day, TrackList с "played at"                                                                                                               |
| `/friends`                   | Friends             | Tabs: Friends / Requests / Suggestions; FriendCard (avatar, name, currently listening), FollowButton                                                  |
| `/friends/requests`          | Friend requests     | Incoming / Outgoing списъци, Accept / Decline                                                                                                          |
| `/profile/[username]`        | Profile             | ProfileHero (avatar, name, @username, bio, stats: friends/followers/following), CurrentlyListening, Recent activity, Public playlists, Top artists    |
| `/notifications`             | Notifications       | Списък с read/unread, групиране по ден, "Mark all as read"                                                                                            |
| `/settings`                  | Settings            | Tabs: Profile / Account / Privacy / Notifications / Playback                                                                                          |
| `/queue`                     | Queue (mobile/full) | Now playing, Next up (drag reorder), Clear                                                                                                           |

---

## 5. Компонентна инвентаризация

### 5.1 Shell / Layout (`components/layout`)

- `AppShell` — grid: topbar / sidebar / main / right panel / player
- `TopBar` — nav history, `GlobalSearch`, `NotificationsButton`, `UserMenu`
- `Sidebar` — `SidebarNav`, `SidebarPlaylists`, collapse toggle
- `RightPanel` — tabs: `NowPlayingPanel`, `QueuePanel`, `FriendsActivityPanel`
- `MobileBottomNav`
- `PageContainer` — padding, max-width, scroll area

### 5.2 Player (`features/player`)

- `PlayerBar` (desktop) — `NowPlayingInfo`, `PlaybackControls`, `ProgressBar`,
  `PlayerExtras` (queue, friends, volume, fullscreen)
- `MiniPlayer` (mobile)
- `FullScreenPlayer` (mobile sheet)
- `PlaybackControls` — shuffle, prev, play/pause, next, repeat
- `ProgressBar` — seekable slider + times
- `VolumeControl`
- `YouTubePlayerAdapter` — скрит `<iframe>` (YouTube IFrame Player API), изложен през
  `PlaybackProvider` интерфейс; **frontend-ът не знае matching логиката**, получава само
  `PlaybackSource { trackId, provider, providerResourceId, playbackType }`
- `usePlayerStore` (Zustand) — queue, current, status, position, shuffle, repeat, volume

### 5.3 Music (`features/music`)

- `MediaCard` — квадратна карта (artwork, title, subtitle, hover Play бутон)
- `MediaCardRow` / `HorizontalSection` — заглавие + "Show all" + ‹ › стрелки + scroll snap
- `MediaHero` — album/playlist/track hero (artwork + meta + dominant-color gradient)
- `ArtistHero` — full-bleed вариант със sticky compact header
- `ActionRow` — Play (голям accent бутон), Shuffle, Like/Save, Share, More (dropdown)
- `TrackTable` — header + `TrackRow` (index/play, artwork, title, artist, album,
  date, ♥, duration, `…` menu); variants: `album | playlist | search | history`
- `TrackList` — компактен списък без header (search top results, track page)
- `TopResultCard`
- `ArtworkImage` — `next/image` wrapper с fallback + blur
- `ExplicitBadge`, `PlaybackUnavailableBadge`
- `LikeButton`, `AddToPlaylistMenu`, `ShareButton`
- `TrackContextMenu` (right-click / `…`)

### 5.4 Search (`features/search`)

- `GlobalSearchInput` (debounced, keyboard shortcut `/`, recent searches dropdown)
- `SearchFilterChips`
- `SearchResults` — секции по тип
- `GenreBrowseGrid`

### 5.5 Library & Playlists (`features/library`, `features/playlists`)

- `LibraryFilterChips`, `LibrarySortMenu`, `ViewToggle`
- `PlaylistCreateDialog`, `PlaylistEditDialog`, `PlaylistCoverUpload`
- `PlaylistOwnerBadge`, `PlaylistVisibilityBadge`

### 5.6 Social (`features/social`)

- `FriendCard`, `FriendRequestCard`, `FollowButton`, `FriendActionMenu`
- `CurrentlyListeningBadge` — "🎧 слуша X — Y" с pulsing dot (real-time)
- `FriendsActivityPanel` / `ActivityFeedItem` — "Иван слуша…", "Мария създаде playlist…"
- `PresenceDot` (online / listening / offline)
- `ProfileHero`, `ProfileStats`

### 5.7 Notifications (`features/notifications`)

- `NotificationBell` (unread count), `NotificationList`, `NotificationItem`

### 5.8 Auth (`features/auth`)

- `LoginForm`, `RegisterForm`, `ForgotPasswordForm`, `ResetPasswordForm`, `OAuthButtons`, `AuthCard`,
  `FormField`, `FormError`; `schemas/auth.ts` (Zod, съобщенията са `auth.errors.*` ключове),
  `lib/errors.ts` (Better Auth code → ключ), `lib/toUserSummary.ts`

### 5.9 UI primitives (`components/ui` — shadcn/ui)

button, input, dialog, dropdown-menu, sheet, tabs, tooltip, slider, avatar, badge,
skeleton, scroll-area, separator, popover, command (за search), context-menu, toast (sonner).

---

## 6. Структура на кода

```
front-end/
  FRONTEND.md                 ← този документ
  messages/
    en.json, bg.json          ← next-intl съобщения (виж §7 i18n)
  src/
    proxy.ts                  ← locale negotiation (Next 16 proxy = middleware)
    i18n/
      routing.ts, navigation.ts, request.ts
    app/
      globals.css             ← Tailwind + design tokens
      [locale]/
      layout.tsx              ← root: locale check, fonts, providers, theme
      (auth)/
        layout.tsx            ← без shell, centered
        login/page.tsx
        register/page.tsx
        forgot-password/page.tsx
        reset-password/page.tsx
      (app)/
        layout.tsx            ← AppShell (sidebar, topbar, player, right panel)
        home/page.tsx
        search/page.tsx
        search/[query]/page.tsx
        artists/[artistId]/page.tsx
        albums/[albumId]/page.tsx
        tracks/[trackId]/page.tsx
        playlists/[playlistId]/page.tsx
        library/page.tsx
        liked/page.tsx
        recently-played/page.tsx
        friends/page.tsx
        friends/requests/page.tsx
        profile/[username]/page.tsx
        notifications/page.tsx
        settings/page.tsx
        queue/page.tsx
      page.tsx                ← redirect → /home или /login
    components/
      ui/                     ← shadcn/ui primitives (генерирани)
      layout/                 ← AppShell, Sidebar, TopBar, RightPanel, MobileBottomNav
      common/                 ← ArtworkImage, EmptyState, ErrorState, PageHeader
    features/
      auth/        { components/, hooks/, api/, schemas/ }
      home/        { components/ }                            ← Home-only pieces (GreetingHeader)
      music/       { components/, hooks/, api/, types/ }      ← catalog: tracks/albums/artists
      search/
      player/      { components/, store/, adapters/youtube/, hooks/ }
      library/
      playlists/
      social/
      recommendations/
      activity/
      notifications/
    lib/
      mock/                   ← TEMPORARY: catalog.ts / social.ts feed the UI until the
                                backend exists. Same shapes as types/. Delete when wired.
      store/
        ui-store.ts           ← Zustand: sidebar collapsed, right panel tab, mobile player
      api/
        client.ts             ← fetch wrapper: base URL, Bearer JWT (по избор), error mapping
        token.ts              ← browser JWT cache (/api/auth/token)
        endpoints.ts
      auth/
        auth.ts               ← Better Auth config (server-only): Postgres `identity`, Google, JWT plugin
        client.ts             ← browser client (signIn / signUp / signOut / useSession)
        session.ts            ← getSession() / getAccessToken() за server components
        mailer.ts             ← Resend (dev: линковете в конзолата), username.ts
      supabase/
        realtime.ts           ← presence / broadcast helpers (само Realtime — Auth не се ползва)
      query/
        provider.tsx          ← TanStack QueryClientProvider
        keys.ts               ← query key factory
      utils/
        format.ts             ← formatDuration, formatCount, relativeTime
        cn.ts
    types/
      api.ts                  ← DTO типове (по-късно генерирани от OpenAPI/packages/api-contracts)
    hooks/                    ← generic hooks (useMediaQuery, useDebounce, useKeyboardShortcut)
    config/
      routes.ts               ← типизирани route helpers
      site.ts
  public/
    fonts/, images/
```

Правила:

- **Всяка feature е самодостатъчна**: components, hooks, api calls, schemas.
  Cross-feature импорти минават през `features/<x>/index.ts` public API.
- `components/ui` съдържа **само** shadcn примитиви. Не се пишат бизнес компоненти там.
- **Няма** гигантска `components/` папка с всичко.

---

## 7. Технически конвенции

### Server vs Client Components

- **Server Components по подразбиране.** Страниците (`page.tsx`) са server, fetch-ват
  initial data и я подават към client компоненти или prefetch-ват в TanStack Query
  (`HydrationBoundary`).
- `"use client"` **само** за: player, интерактивни списъци (hover play, drag), forms,
  realtime subscriptions, Zustand consumers, search input.

### Data fetching

- **TanStack Query** за целия server state. Query keys през `lib/query/keys.ts`
  factory: `keys.tracks.detail(id)`, `keys.search.results(q, type)`.
- `lib/api/client.ts` — единствената точка, която говори с backend-а. Добавя
  `Authorization: Bearer <supabase JWT>`, `X-Request-Id`, мапва `{code, message, traceId}`
  към `ApiError`.
- Mutations с optimistic updates за like / follow / add-to-playlist.
- Cursor pagination → `useInfiniteQuery`.

### Client state

- **Zustand** само за: `playerStore` (queue, current, status, position, volume, shuffle,
  repeat), `uiStore` (sidebar collapsed, right panel tab, mobile player expanded).
- Нищо друго не отива в глобален store без основание.

### Realtime

- Supabase Realtime **Presence** за online/listening state на приятели.
- Supabase Realtime **Broadcast** за `PLAYBACK_STARTED / PAUSED / CHANGED / STOPPED`.
- Един `RealtimeProvider` в app shell-а, който subscribe-ва към каналите и пише в
  `presenceStore` / invalidate-ва queries. **Не** се записва position всяка секунда.

### Forms

- React Hook Form + Zod. Схемите живеят във `features/<x>/schemas/`.

### Auth

- **Better Auth** (Project-Info.md §8, ADR-011) — не Supabase Auth, не Clerk. Конфигурацията е
  `lib/auth/auth.ts` (server-only), route handler-ът `app/api/auth/[...all]/route.ts`, browser
  клиентът `lib/auth/client.ts` (`signIn`, `signUp`, `signOut`, `useSession`).
- Email + парола и Google (`OAuthButtons`). Username plugin: всеки потребител има `username`
  (Google sign-up го получава от local part-а на имейла през `lib/auth/username.ts`).
- Session: httpOnly cookie. Server components четат `getSession()` от `lib/auth/session.ts`
  (memoized per request). `proxy.ts` bounce-ва без cookie към `/login?next=…` (само проверка
  дали има cookie); `(app)/layout.tsx` прави истинската проверка; `(auth)/layout.tsx` връща
  вече влезлите към Home.
- JWT за Spring: `/api/auth/token` (RS256, 15 min, `aud=aura-api`, `sub`=user UUID). В browser-а
  `lib/api/token.ts` го кешира в паметта и се подава на `apiFetch(path, { token })`; в server
  components — `getAccessToken()`. Никога в localStorage.
- Данни: схема `identity` в общия Postgres; `npm run auth:migrate` прилага диффа, SQL-ът е в
  `db/auth/`. Имейли (верификация, reset) през Resend — без `RESEND_API_KEY` линковете се печатат
  в server конзолата.
- Никакви provider secrets във frontend-а. Server-only env: `BETTER_AUTH_SECRET`, `DATABASE_URL`,
  `GOOGLE_CLIENT_ID/SECRET`, `RESEND_API_KEY`. Публични: `NEXT_PUBLIC_APP_URL`,
  `NEXT_PUBLIC_API_BASE_URL`.

### Playback

- `PlaybackProvider` интерфейс: `load(source)`, `play()`, `pause()`, `seek(ms)`,
  `setVolume()`, events: `onReady / onStateChange / onProgress / onEnded / onError`.
- `YouTubePlaybackProvider` имплементира го през official IFrame Player API.
  Iframe е скрит (или показан в Now Playing panel, ако terms го изискват — ще се
  реши в compliance gate).
- Listening events (`TRACK_STARTED / COMPLETED / SKIPPED`) се изпращат към backend от
  player-а при съответните преходи, **не** на всяка секунда.

### i18n (next-intl)

- Локали: `en` (default) и `bg`. **Всеки URL носи locale prefix**: `/en/home`, `/bg/home`
  (`localePrefix: "always"`). `/` и `/home` се пренасочват от `src/proxy.ts` според
  `NEXT_LOCALE` cookie / `Accept-Language`.
- Route дърво: `src/app/[locale]/…` — `[locale]/layout.tsx` е root layout (валидира locale,
  `setRequestLocale`, `NextIntlClientProvider`).
- Съобщения: `messages/en.json`, `messages/bg.json`. Namespace-ове: `app`, `common`, `nav`,
  `search`, `panel`, `presence`, `player`, `home`, `time`, `pages`. Ключовете са типизирани
  (`src/types/next-intl.d.ts`) — грешен ключ е compile error.
- **Навигация само през `@/i18n/navigation`** (`Link`, `useRouter`, `usePathname`, `redirect`),
  никога `next/link` / `next/navigation` — иначе се губи locale prefix-ът. `routes.ts` остава
  без locale; `Link` го добавя сам.
- Server components: `getTranslations()` / `setRequestLocale(locale)` във всяка page (нужно за
  static rendering). Client components: `useTranslations("ns")`. Дати/числа: `useFormatter()`.
- Rich text с линкове: `t.rich("key", { name: () => <Link/> })` (виж `FriendsActivityPanel`).
- Превключвател: `LanguageMenuItems` в user менюто — `router.replace(pathname, { locale })`.
- Данните от catalog (заглавия, описания на playlists) НЕ се превеждат — те са съдържание.

### Styling

- Tailwind v4 + CSS variables за токените от §2 в `globals.css`.
- `cn()` (clsx + tailwind-merge).
- Icons: `lucide-react`.
- Animations: Tailwind transitions + `motion` (framer-motion) само за player sheet и
  hero-parallax.

### Accessibility & UX

- Keyboard: `Space` play/pause (когато фокусът не е в input), `/` фокус върху search,
  `←/→` seek ±5s, `Esc` затваря панели.
- Всички icon-only бутони имат `aria-label` + tooltip.
- Skeleton loaders за всяка секция; `EmptyState` и `ErrorState` компоненти.

### Testing

- Vitest + React Testing Library за компоненти и hooks.
- Playwright за критичните flows: login → search → play → like.

---

## 8. Ред на имплементация (frontend)

Следва vertical slice-а от `Project-Info.md §56`.

1. **App shell + design tokens** — layout, sidebar, topbar, player bar (статичен),
   right panel, mobile nav. Всички страници като празни route-ове.
2. **Auth** — login / register / forgot-password с Better Auth (готово: email+парола, Google).
3. **Home** — секции с mock данни → реални данни.
4. **Search** — global search + results page.
5. **Artist / Album / Track pages**.
6. **Player** — YouTube adapter, queue, controls, listening events.
7. **Realtime** — currently listening, friends panel.
8. **Library / Liked / Playlists**.
9. **Friends / Profile / Notifications / Settings**.
10. **Recommendations** секции в Home.

Всяка стъпка завършва с: build ✓, lint ✓, типове ✓, responsive проверка ✓.

---

## 9. Външни ресурси, които може да са полезни

- Fonts: Geist / Geist Mono (Vercel, OFL) или Inter / Inter Tight (Google Fonts).
- Icons: lucide-react.
- Placeholder artwork при mock фаза: генерирани gradient placeholders (без външни
  copyright изображения в repo-то).
- YouTube IFrame Player API docs — за `PlaybackProvider` адаптера.
- Supabase Realtime Presence/Broadcast docs.
