# TODO — Текст на песните (Lyrics) + Full-screen "Сега слушаш"

Записано на 2026-09-14, за работа на 2026-09-15.

---

## 1. Какво установихме

### TIDAL — НЕ дава текстове на нашия тип достъп

- Endpoint-ът съществува: `GET /tracks/{id}/relationships/lyrics?include=lyrics` (JSON:API, `lyrics` relationship).
- С client-credentials token (нашия server-to-server достъп) връща **200 с празен `data: []` за всяка песен**.
  Тествано: Shape of You, Blinding Lights, Levitating, Birds of a Feather, Drama Queen.
- `GET /lyrics/{id}` → 404.
- Причина: TIDAL лицензира текстовете от Musixmatch и не ги предоставя на трети приложения без
  user OAuth (и дори тогава е несигурно). **Не разчитаме на TIDAL за lyrics.**

### LRCLIB (https://lrclib.net) — работи, проверено

- Свободна, community-сорсвана база със **синхронизирани** текстове (LRC формат: `[mm:ss.xx] ред`).
- Публично API, **без ключ**, без rate-limit документ (искат само смислен `User-Agent`).
- Lookup: `GET https://lrclib.net/api/get?artist_name=&track_name=&album_name=&duration=`
  - `duration` е в секунди; толеранс ±2s от тяхна страна.
  - Отговор: `{ id, trackName, artistName, albumName, duration, instrumental, plainLyrics, syncedLyrics }`
  - 404 `{"statusCode":404,"name":"TrackNotFound"}` ако няма.
  - Има и `GET /api/search?q=` / `?track_name=&artist_name=` за fuzzy търсене (връща масив) — за fallback.
- Резултати от теста (2026-09-14):

  | Песен | Резултат |
  |---|---|
  | Grafa – Drama Queen (249s) | ✓ synced (`[00:13.90] Аз те моля нищо да не казваш…`) |
  | Lubo Kirov – Znam (213s) | ✓ synced (`[00:01.04] Гледам към небето…`) |
  | Billie Eilish – BLUE (343s) | ✓ synced |
  | The Weeknd – Blinding Lights (200s) | ✓ synced |
  | Atanas Kolev – Шах и мат (211s) | ✗ TrackNotFound (и с кирилица, и с латиница) |

- **Лицензионна уговорка**: текстовете са copyright; LRCLIB е ОК за dev/MVP, но за комерсиален
  launch трябва лицензиран доставчик (виж Musixmatch). Затова — зад порт, сменяем.

### Musixmatch (https://developer.musixmatch.com) — за production

- Индустриалният доставчик (Spotify, TIDAL, Apple ползват него). Лицензирани текстове.
- Free plan: само 30% от текста (preview), **без** synced. Synced (`track.subtitle.get`, `track.richsync.get`)
  е в платените планове; нужна е регистрация + одобрение на приложението.
- API: `track.search` / `matcher.track.get` (по artist+title, или по ISRC — ние имаме ISRC за повечето
  песни от TIDAL!), после `track.lyrics.get` / `track.subtitle.get` (LRC-подобен формат).
- Изисква да показваме техния copyright/tracking pixel (`lyrics_copyright`, `pixel_tracking_url`).
- **Действие за утре (по избор)**: регистрация + да видим цени за synced план.

---

## 2. Backend — catalog-svc

### 2.1 Порт + provider (hexagonal, както при TIDAL/YouTube)

```
domain/port/LyricsProvider.java
    Optional<ProviderLyrics> find(LyricsQuery q)   // q: artistNames, title, albumTitle, durationMs, isrc
domain/model/Lyrics.java
    record Lyrics(UUID trackId, String provider, boolean instrumental,
                  List<SyncedLine> synced /* nullable */, String plain /* nullable */, Instant fetchedAt)
    record SyncedLine(long timeMs, String text)
adapter/provider/lrclib/LrclibLyricsProvider.java    (@ConditionalOnProperty aura.lyrics.provider=lrclib)
adapter/provider/lrclib/LrclibApiClient.java         (RestClient, User-Agent header, retry/circuit breaker
                                                      по модела на TidalApiClient, timeouts от spring.http.client)
adapter/provider/musixmatch/...                      (по-късно; същият порт)
```

### 2.2 Lookup стратегия (LRCLIB)

1. `api/get` с `artist_name = primaryArtist.name`, `track_name = title`, `album_name = album.title`, `duration = durationMs/1000`.
2. Ако 404 → `api/get` **без** `album_name` (албумите често се различават — compilation vs single).
3. Ако 404 и заглавието/артистът са на кирилица → опит с транслитерация (има готова таблица в
   `playback-svc TrackMatcher.transliterate`; да се извади в общ helper или да се копира — без shared domain).
4. Ако 404 → `api/search?track_name=&artist_name=` и вземи първия резултат с `|duration - ours| <= 5s`.
5. Ако пак нищо → записваме "няма" (виж кеш), за да не питаме повторно.
- Парсване на LRC: редове `[mm:ss.xx]text`; един ред може да има няколко timestamp-а `[00:10.00][00:40.00]текст`
  → дублира се. Празни редове/`♪` се пазят като инструментални паузи.

### 2.3 Кеш в базата (нова Flyway миграция `V9__track_lyrics.sql`)

```sql
CREATE TABLE catalog.track_lyrics (
    track_id      UUID PRIMARY KEY REFERENCES catalog.tracks (id) ON DELETE CASCADE,
    provider      TEXT NOT NULL,            -- 'LRCLIB' | 'MUSIXMATCH' | 'NONE' (търсено, няма)
    instrumental  BOOLEAN NOT NULL DEFAULT FALSE,
    synced        JSONB,                    -- [{"t":13900,"text":"..."}] или NULL
    plain         TEXT,
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
- `provider='NONE'` ред = "проверихме, няма" → ретрай след напр. 30 дни (`aura.lyrics.retry-missing-after`).
- Намерен текст се пази за постоянно (refresh не е нужен).

### 2.4 Service + endpoint

- `domain/service/LyricsService.getLyrics(UUID trackId)`:
  кеш → ако няма/изтекъл NONE → провайдър → upsert → връщане. При provider outage → 503-подобно
  (`PROVIDER_UNAVAILABLE`), без да записва NONE.
- `GET /api/v1/catalog/tracks/{id}/lyrics` →
  `200 { trackId, provider, instrumental, synced: [{timeMs, text}] | null, plain: string | null }`
  `404 { code: "LYRICS_NOT_FOUND" }` когато провайдърът няма текст.
- Config `aura.lyrics.provider: lrclib`, `aura.lyrics.lrclib.base-url`, `user-agent`,
  `retry-missing-after: 30d`. Gateway route `/api/v1/catalog/**` вече го покрива.
- (По желание) prefetch: когато `playback-svc` резолне песен успешно, catalog-svc да дърпа текста
  в background — за да е готов преди play. Не е задължително за v1 (lookup-ът е ~200ms).

### 2.5 Правила от CLAUDE.md

- Без тестове. Проверка с `bootRun` + curl.

---

## 3. Frontend — Full-screen "Сега слушаш" с текст

### 3.1 API слой

- `types/api.ts`: `LyricsDto { trackId, provider, instrumental, synced: {timeMs, text}[] | null, plain: string | null }`
- `features/player/api/lyricsApi.ts`: `getLyrics(trackId)` → `Lyrics | null` (404 → null, както `resolvePlaybackSource`).
- Кеш в паметта по trackId (Map) — една заявка на песен за сесията.

### 3.2 Екран `FullscreenPlayer` (`features/player/components/FullscreenPlayer.tsx`)

- Отваря се от бутона за разгъване долу вдясно в `PlayerBar` (иконата вече съществува) и с `Esc` се затваря;
  състояние `fullscreenPlayerOpen` в `ui-store` (не persist).
- Layout (desktop, ≥lg): две колони.
  - Ляво: голяма обложка **или видеото** — преизползва video surface-а: рендерира slot `div` и го
    регистрира в `useVideoSurfaceStore.setSlot` точно както `NowPlayingPanel`; превключвателят
    Обложка/Видео е същият (`nowPlayingVideo`). Под него: заглавие, артисти, албум · година.
  - Дясно: текстът.
    - Synced: списък редове; текущият (последният с `timeMs <= positionMs`) е голям, ярък, бял;
      предишните — затъмнени; следващите — сиви. Авто-скрол така, че текущият ред да е ~40% от
      височината (smooth). Клик на ред → `seek(timeMs)`.
    - Само plain: рендер като параграфи, без highlight.
    - `instrumental` или няма → само обложка/видео центрирано + мета, без празна колона.
  - Фон: `bg-gradient-hero` + затъмнена, размазана обложка (`blur-3xl`, `opacity-30`) под всичко.
  - Долу: същият `PlaybackControls` + `ProgressBar` (или компактен ред), за да не се излиза от екрана за пауза.
- Mobile (<lg): една колона — обложка/видео отгоре (квадрат), текстът отдолу; това по-късно се слива с
  "expandable full player" от Project-Info §39/§46 (`mobilePlayerExpanded` вече съществува в ui-store).
- Позицията идва от `usePlayerStore(s => s.positionMs)` (tick на 250ms) — достатъчно за ред по ред.
- Достъпност: `role="dialog" aria-modal`, фокус в диалога, `Esc` затваря, `aria-current` на текущия ред.
- i18n: `player.lyricsUnavailable`, `player.instrumental`, `player.closeFullscreen`, `player.lyricsBy` ("Текст: LRCLIB").
  При Musixmatch — задължителен copyright ред от отговора им.

### 3.3 Дребни неща

- В `NowPlayingPanel` (дясното меню) може да се покаже само "следващият ред" под мета-то като тийзър —
  по желание, след като full-screen работи.
- `VideoSurfaceOverlay` вече покрива непускащите състояния; във full-screen slot-ът е по-голям —
  провери `VIDEO_CROP_ZOOM` (1.4) дали още реже YouTube лентите при голям размер.

---

## 4. Ред на работа за утре

1. Backend: миграция V9 → порт/модел → `LrclibApiClient` + provider → `LyricsService` → endpoint → `bootRun` + curl
   (провери с Drama Queen (synced), Шах и мат (404), инструментал).
2. Frontend: `lyricsApi` → `FullscreenPlayer` (първо със synced highlight, после plain/empty състояния) →
   бутон в `PlayerBar` → Esc/фокус → mobile подредба.
3. Проверка в браузъра: highlight следва позицията, клик на ред превърта, видео режимът работи и там.
4. Отделно решение: регистрация в Musixmatch + цени за synced план (за production).
