# MASTER PROMPT — MUSIC SOCIAL STREAMING PLATFORM

Ти си lead software architect, senior backend engineer и senior full-stack engineer.

Твоята задача е да проектираш и постепенно да имплементираш production-grade music social streaming platform.

Това НЕ е обикновено CRUD приложение и НЕ трябва да бъде реализирано като един огромен monolith.

Искаме реална, добре структурирана система с ясни bounded contexts, микросървиси, asynchronous communication, caching, observability, resilience и ясна ownership граница на данните.

В същото време НЕ искаме microservices само заради самите microservices.

Архитектурата трябва да бъде сериозна, но прагматична.

==================================================

1. КАКВО ПРЕДСТАВЛЯВА ПРИЛОЖЕНИЕТО
   ==================================================

Изграждаме собствена music-social платформа, вдъхновена от функционалността на Spotify и TIDAL.

Приложението трябва да комбинира:

* music discovery
* music search
* artists
* albums
* tracks
* playlists
* liked songs
* personal library
* listening history
* recently played
* recommendations
* friends
* friend requests
* follows
* social activity
* currently listening
* notifications
* user profiles
* queue
* shuffle
* repeat
* music player
* real-time social presence

Основната идея е:

MUSIC = центърът на социалния graph.

Потребителите не просто слушат музика.

Те:

* виждат какво слушат приятелите им
* откриват музика чрез приятелите си
* споделят playlists
* следват хора
* виждат activity
* получават персонализирани recommendations
* изграждат собствена music identity

Пример:

Потребител А слуша:

Billie Eilish — Blue

Приятелите му могат да видят:

"Антоан слуша Billie Eilish — Blue"

Този state трябва да бъде real-time.

Дългосрочните listening events трябва да бъдат persist-нати.

==================================================
2. ВАЖНА РАЗЛИКА: НЕ СМЕ YOUTUBE FRONTEND
=========================================

Нашето приложение НЕ трябва да бъде:

"обвивка върху YouTube"

и НЕ трябва да бъде:

"обвивка върху TIDAL".

Ние притежаваме собствените си:

* users
* friendships
* follows
* playlists
* likes
* listening history
* activity
* recommendations
* application state
* normalized catalog representation
* playback matching logic
* user experience

Външните music providers са външни зависимости.

==================================================
3. TECH STACK
=============

Frontend:

* Next.js
* React
* TypeScript
* Tailwind CSS
* shadcn/ui
* TanStack Query
* Zustand, когато е необходимо
* React Hook Form
* Zod

Backend:

* Java 25 LTS
* Spring Boot
* Spring Security
* Spring Cloud Gateway
* Spring Data JDBC/JPA само когато има реална полза
* REST APIs
* RabbitMQ
* Redis

Backend deployment:

* Render

Frontend deployment:

* Cloudflare
* Cloudflare Pages / подходящия актуален Cloudflare deployment модел за Next.js

Database / platform:

* Supabase
* PostgreSQL
* Better Auth (identity; вж. §8) — не Supabase Auth
* Supabase Realtime
* Supabase Storage

External music metadata:

* TIDAL

External playback source discovery:

* YouTube Data API

Email:

* Resend

Domain:

* Spaceship

Observability:

* OpenTelemetry
* Micrometer
* Spring Boot Actuator
* Prometheus-compatible metrics
* Grafana-compatible dashboards
* structured JSON logging

==================================================
4. DEPLOYMENT ARCHITECTURE
==========================

Frontend и backend са напълно отделни deployment layers.

Frontend:

Browser
|
v
Cloudflare
|
v
Next.js

Backend:

Browser
|
v
Cloudflare
|
v
API Gateway
|
v
Spring Boot services
|
v
Render

Spring Boot microservices ще се deploy-ват на Render като отделни services/containers.

Не използвай Cloudflare Workers за Spring Boot.

Cloudflare е edge/frontend layer.

Render е основният execution/deployment слой за backend-а.

Backend архитектурата трябва да остане portable.

Да бъде възможно по-късно deployment-ът да бъде преместен към:

* AWS
* GCP
* Kubernetes
* Fly.io
* друг container platform

без преработка на domain layer-а.

==================================================
5. SUPABASE СТРАТЕГИЯ
=====================

Използваме ЕДИН Supabase project.

Той предоставя:

* PostgreSQL
* ~~Auth~~ (не се ползва — самоличността е Better Auth, §8)
* Realtime
* Storage

НЕ създавай отделен Supabase project за всеки microservice.

НЕ създавай отделна Supabase Realtime система за всеки microservice.

Причината е operational complexity.

Искаме:

ONE Supabase project
+
ONE PostgreSQL environment
+
ONE Realtime layer
+
logical ownership между services.

==================================================
6. DATABASE OWNERSHIP
=====================

Всеки microservice трябва да има ясна ownership граница на данните.

Използваме PostgreSQL schemas.

Пример:

social
catalog
playback
library
recommendation
activity
notifications

Всеки service притежава собствената си schema.

Пример:

Social Service
-> social.*

Music Catalog Service
-> catalog.*

Playback Resolver
-> playback.*

Library Service
-> library.*

Recommendation Service
-> recommendation.*

Activity Service
-> activity.*

Notification Service
-> notifications.*

Друг service НЕ трябва директно да модифицира чужди таблици.

Cross-service interaction трябва да става чрез:

* REST API
* domain events
* message broker
* read models
* ясно дефинирани integration contracts

Използвай database roles/permissions, така че всеки service да има само необходимите database privileges.

==================================================
7. ЗАЩО НЕ ПРАВИМ PHYSICAL DATABASE PER SERVICE
===============================================

В първата версия НЕ искаме:

Supabase instance #1
PostgreSQL #1

Supabase instance #2
PostgreSQL #2

etc.

Това би създало ненужна operational complexity.

Вместо това искаме:

ONE PostgreSQL cluster
+
logical service ownership.

Ако в бъдеще един bounded context стане прекалено голям или има специфични scalability/security requirements, той може да бъде преместен в отделна физическа база.

Архитектурата трябва да позволява това.

==================================================
8. AUTHENTICATION
=================

Better Auth е identity provider (ADR-011, 2026-09-18 — замени Supabase Auth; Clerk и Supabase
Auth са отхвърлени, за да не зависим от външен доставчик за самоличността).

Better Auth живее в Next.js (`front-end/src/lib/auth/auth.ts`, endpoints под `/api/auth/*`) и
притежава схемата `identity` в общия Supabase Postgres (`identity.user`, `session`, `account`,
`verification`, `jwks`). Схемата се прилага с `npm run auth:migrate`; SQL-ът е в
`front-end/db/auth/`. Не се ползва схемата `auth` — тя е резервирана от Supabase за неговия
GoTrue и `postgres` ролята няма права в нея.

Методи: email + парола (с верификация и reset по имейл през Resend) и Google OAuth.

НЕ създавай собствена система за password authentication — Better Auth я дава наготово.

User authentication:

Next.js (Better Auth)
|
v
session cookie (httpOnly) за UI-а  +  JWT (RS256, 15 min, aud=aura-api) за API-то
|
v
API Gateway (forward-ва Authorization: Bearer)
|
v
Spring Security (oauth2-resource-server, jwk-set-uri = <APP_URL>/api/auth/jwks)

Spring Boot services трябва да валидират Better Auth JWT офлайн през JWKS — никакви обръщения
към Next.js за всяка заявка. Claims: `sub` (user UUID), `email`, `username`, `name`, `iss`
(NEXT_PUBLIC_APP_URL), `aud` ("aura-api").

`identity.user.id` (UUID, `sub` в JWT-то) е canonical user ID. Другите services го пазят като
`uuid` колона и никога не дублират identity данни — profile/friendship информацията е на
Identity & Social service, ключирана по това UUID.

Spring Boot никога не трябва да се доверява на userId, подаден от frontend-а.

Identity трябва да се извлича от authenticated JWT.

Spring Security трябва да управлява:

* authentication
* authorization
* resource ownership
* roles
* permissions

Никога не изпращай:

* Supabase service-role key
* BETTER_AUTH_SECRET, DATABASE_URL, Google client secret
* TIDAL secret
* YouTube secret
* Resend secret

към browser-а.

==================================================
9. MICROSERVICE АРХИТЕКТУРА
===========================

Първоначално системата трябва да има 7 deployable backend компонента:

1. API Gateway
2. Identity & Social Service
3. Music Catalog Service
4. Playback Resolver Service
5. Library & Playlist Service
6. Recommendation Service
7. Activity & Notification Service

НЕ разделяй тези services допълнително без реална причина.

==================================================
10. API GATEWAY
===============

Technology:

Spring Cloud Gateway

Отговорности:

* единна backend entry point
* routing
* authentication forwarding
* rate limiting
* request correlation
* request IDs
* API versioning
* basic security
* observability

Примерни routes:

/api/v1/social/**
/api/v1/catalog/**
/api/v1/playback/**
/api/v1/library/**
/api/v1/recommendations/**
/api/v1/activity/**
/api/v1/notifications/**

Gateway НЕ трябва да съдържа business logic.

Gateway няма собствена database.

==================================================
11. IDENTITY & SOCIAL SERVICE
=============================

Този service управлява application-level user/social data.

Отговорности:

* profiles
* username
* display name
* avatar reference
* bio
* friendships
* friend requests
* follows
* blocks
* privacy settings
* social preferences

Не съхранявай passwords.

Предложени таблици:

social.user_profiles
social.friendships
social.friend_requests
social.follows
social.blocks
social.user_settings

==================================================
12. MUSIC CATALOG SERVICE
=========================

Това е canonical music metadata service.

Основен metadata provider:

TIDAL

Но business logic НЕ трябва да бъде директно зависима от TIDAL DTO-та.

Създай abstraction:

MusicMetadataProvider

Пример:

interface MusicMetadataProvider {

```
search(...)

getTrack(...)

getAlbum(...)

getArtist(...)
```

}

Създай:

TidalMetadataProvider

Всички TIDAL-specific модели трябва да останат вътре в provider adapter слоя.

Вътрешният domain модел трябва да бъде provider-agnostic.

==================================================
13. CANONICAL MUSIC MODEL
=========================

Вътрешен Track модел:

Track

* internalId
* title
* durationMs
* isrc
* releaseDate
* explicit
* albumId
* primaryArtistId
* artwork
* providerReferences
* createdAt
* updatedAt

Artist:

* internalId
* name
* artwork
* providerReferences

Album:

* internalId
* title
* releaseDate
* artwork
* artistId
* providerReferences

ProviderReference:

* provider
* providerResourceId

НЕ използвай:

TIDAL Track ID

като internal primary key.

Нашите идентификатори трябва да са независими.

==================================================
14. TIDAL НЕ Е НАШАТА DATABASE
==============================

Не импортирай целия TIDAL catalog.

Нашата PostgreSQL база е:

application-owned normalized music catalog/cache.

Новите песни не трябва да изискват manual import.

Работим с lazy discovery.

Пример:

User търси:

Billie Eilish Blue

```
|
v
```

Music Catalog Service
|
+---- local catalog
|
+---- ако няма резултат или metadata е stale
|
v
TIDAL API
|
v
normalize
|
v
persist
|
v
return

След като песента бъде открита:

бъдещите заявки използват нашия catalog/cache вместо постоянно да питат TIDAL.

Използвай TTL стратегия.

Но НЕ приемай автоматично, че всяка TIDAL metadata може да бъде пазена завинаги.

Спазвай текущите TIDAL Developer Terms.

==================================================
15. TIDAL PROVIDER ABSTRACTION
==============================

Архитектура:

MusicMetadataProvider
|
+---- TidalMetadataProvider
|
+---- FutureProvider

Бизнес логиката не трябва да знае дали metadata идва от TIDAL или друг provider.

Това позволява по-късно да заменим TIDAL без major rewrite.

==================================================
16. PLAYBACK RESOLVER SERVICE
=============================

Playback Resolver Service НЕ определя какво е песента.

Той отговаря само:

"Кой външен playback source най-вероятно представлява този track?"

В нашия MVP playback source provider е:

YouTube

Flow:

Canonical Track
|
v
Playback Resolver
|
v
YouTube search
|
v
candidate videos
|
v
matching algorithm
|
v
best candidate
|
v
PlaybackSource

==================================================
17. YOUTUBE MATCHING АРХИТЕКТУРА
================================

НИКОГА не взимай автоматично първия YouTube search result.

Търсим кандидати.

Използвай:

* track title
* artist name
* duration
* ISRC, когато е наличен
* channel
* channel trust
* title normalization
* artist normalization
* YouTube metadata
* keywords
* negative keywords
* embeddability
* music category, когато е приложимо

Позитивни сигнали:

* exact title
* normalized title
* exact artist
* normalized artist
* ISRC match
* official artist channel
* recognized label channel
* Official Audio
* Official Music Video
* duration similarity

Негативни сигнали:

* cover
* live
* remix
* slowed
* reverb
* nightcore
* karaoke
* instrumental
* acoustic version
* sped up
* fan upload
* reaction
* compilation
* mashup

Duration никога не трябва да бъде единствен критерий.

==================================================
18. MATCHING SCORE
==================

Използвай deterministic scoring system.

Примерна начална конфигурация:

ISRC exact match:
+100

Official artist channel:
+40

Official label channel:
+30

Exact artist match:
+25

Exact title match:
+25

Duration difference <= 1 sec:
+20

Duration difference <= 3 sec:
+10

"Official Audio":
+15

"Official Music Video":
+10

Negative signals:

Live:
-40

Cover:
-50

Remix:
-40

Slowed:
-40

Reverb:
-30

Karaoke:
-50

Instrumental:
-50

Nightcore:
-50

Reaction:
-100

Тези стойности са начална конфигурация и трябва да бъдат configurable.

Пример:

score >= HIGH_CONFIDENCE
-> trusted automatic match

MEDIUM <= score < HIGH
-> candidate / pending verification

score < MEDIUM
-> reject

НЕ позволявай системата да избира low-confidence result само защото е единственият резултат.

По-добре:

"Playback unavailable"

отколкото грешна версия на песента.

==================================================
19. YOUTUBE MATCH DATA
======================

playback.track_sources:

* id
* trackId
* provider
* providerResourceId
* title
* channelId
* channelTitle
* durationMs
* matchScore
* matchMethod
* isVerified
* verifiedAt
* createdAt
* updatedAt

Трябва да можем да разберем:

* защо е избран този резултат
* с какъв confidence
* кога е намерен
* чрез какъв matching method
* дали е verified

==================================================
20. YOUTUBE QUOTA MANAGEMENT
============================

YouTube API quota е critical infrastructure concern.

НЕ прави:

User search
->
YouTube search.list
->
винаги

Това ще изчерпи quota много бързо.

Използвай:

* PostgreSQL cache
* Redis cache
* request deduplication
* request coalescing
* background jobs
* rate limiter
* quota tracker
* retries
* backoff
* circuit breaker

Пример:

Track
|
+---- YouTube source already known?
|
+---- YES -> return immediately
|
+---- NO
|
v
resolution job
|
v
YouTube API
|
v
persist match

Ако 100 users едновременно поискат една и съща unresolved песен:

НЕ прави:

100 YouTube searches

Прави:

1 resolution job

След resolve:

всички заявки използват общия резултат.

==================================================
21. AUTOMATIC DISCOVERY OF NEW SONGS
====================================

Няма manual import на всички песни.

Нова песен може да бъде открита по няколко начина:

1. user search
2. new release discovery
3. recommendations
4. playlists
5. friend activity
6. background synchronization

Пример:

New Track discovered
|
v
Music Catalog
|
v
persist normalized track
|
v
Playback source = NULL
|
v
Playback Resolution Job
|
v
YouTube Matching
|
v
persist source

Така новите песни автоматично се добавят към application catalog-а, когато бъдат открити.

==================================================
22. BACKGROUND JOBS
===================

Използвай RabbitMQ за durable asynchronous workflows.

Примерни jobs:

* resolve-youtube-source
* generate-recommendations
* process-listening-event
* update-activity
* send-notification-email
* refresh-metadata

Потребителският request НЕ трябва да чака ненужен background процес.

==================================================
23. LIBRARY & PLAYLIST SERVICE
==============================

Отговорности:

* playlists
* playlist items
* liked tracks
* saved albums
* saved artists

Таблици:

library.playlists
library.playlist_items
library.liked_tracks
library.saved_albums
library.saved_artists

Не копирай целия Track object във всяка playlist row.

Използвай internal Track ID reference.

В бъдеще playlist системата трябва да може да поддържа:

* public/private playlists
* shared playlists
* collaborative playlists
* sharing links

==================================================
24. RECOMMENDATION SERVICE
==========================

Препоръките са отделен bounded context.

Първа версия:

rule-based recommendations

Използвай:

* liked tracks
* liked artists
* liked genres
* recent listening
* skips
* repeats
* saved albums
* friend activity
* popular tracks
* new releases

След това:

vector similarity
+
embeddings
+
ranking

Ако използваме pgvector, той може да остане в същия PostgreSQL environment, но recommendation schema трябва да си остане собственост на Recommendation Service.

НЕ използвай provider-restricted content за machine learning, embeddings или AI processing, освен ако Terms на съответния provider изрично го позволяват.

==================================================
25. ACTIVITY & NOTIFICATION SERVICE
===================================

Този service управлява:

* social activity feed
* notifications
* read/unread state
* user activity events

Примерни events:

USER_FRIEND_REQUESTED
USER_FRIEND_ACCEPTED
USER_FOLLOWED
PLAYLIST_CREATED
PLAYLIST_SHARED
TRACK_LIKED
PLAYBACK_STARTED
PLAYBACK_COMPLETED
PLAYBACK_SKIPPED

Service-ите не трябва да пишат директно Activity tables на друг service.

Използвай domain events.

==================================================
26. EVENT-DRIVEN ARCHITECTURE
=============================

Използвай RabbitMQ.

Kafka НЕ е необходим на първия етап.

Events трябва да бъдат:

* immutable
* versioned
* idempotent
* traceable

Всеки event трябва да има:

* eventId
* eventType
* eventVersion
* timestamp
* source
* payload

Пример:

TrackPlayedEvent

{
eventId,
eventVersion,
userId,
trackId,
sessionId,
timestamp,
source
}

==================================================
27. OUTBOX PATTERN
==================

За важни transactional events използвай Outbox Pattern.

Пример:

Database transaction:

1. save playlist change
2. save outbox event

След това background publisher:

Outbox
->
RabbitMQ
->
consumers

НЕ разчитай на:

database write succeeded
+
message publish succeeded

без transactional гаранция.

==================================================
28. REALTIME АРХИТЕКТУРА
========================

Supabase Realtime ще бъде единен real-time layer.

Основни use cases:

* presence
* currently listening
* friend activity
* notifications
* lightweight live state

НЕ използвай PostgreSQL за всяка секунда от player progress.

НЕ записвай:

player_position = 124 sec
player_position = 125 sec
player_position = 126 sec

в DB постоянно.

Вместо това:

Realtime:

PLAYBACK_STARTED
PLAYBACK_PAUSED
PLAYBACK_CHANGED
PLAYBACK_STOPPED

а durable listening events:

TRACK_STARTED
TRACK_COMPLETED
TRACK_SKIPPED

се persist-ват.

==================================================
29. CURRENTLY LISTENING
=======================

Примерен state:

{
userId,
status,
trackId,
startedAt
}

status:

online
offline
listening

Това е ephemeral state.

Supabase Realtime Presence/Broadcast трябва да бъде предпочитан за него.

Постоянната информация се записва само когато има business value.

==================================================
30. REDIS
=========

Redis е cache и coordination layer.

Използвай Redis за:

* catalog caching
* search caching
* provider response caching
* playback resolution locks
* request coalescing
* distributed rate limiting
* quota counters
* idempotency keys
* temporary state

НЕ използвай Redis като primary database.

==================================================
31. SEARCH
==========

В началото не въвеждай Elasticsearch/OpenSearch.

Първа версия:

PostgreSQL full-text search
+
pg_trgm / подходящи индекси

Search targets:

* tracks
* artists
* albums
* playlists
* users

Резултатите трябва да бъдат rank-нати.

Приоритет:

1. exact match
2. prefix match
3. normalized match
4. popularity
5. user history
6. user's taste
7. friend activity

Search system трябва да бъде абстрахиран така, че по-късно PostgreSQL да може да бъде заменен с OpenSearch/Elasticsearch без промяна на frontend API.

==================================================
32. LISTENING HISTORY
=====================

Отделяй:

ephemeral playback state
от
durable listening history.

Listening event:

{
userId,
trackId,
sessionId,
startedAt,
completedAt,
durationPlayedMs,
completed,
source
}

Не записвай player state на всяка секунда.

Listening history трябва да може да служи за:

* recently played
* recommendations
* statistics
* activity

==================================================
33. EMAIL
=========

Използвай Resend.

Примерни emails:

* welcome
* security
* friend request
* friend accepted
* playlist shared
* optional notifications

Email sending трябва да бъде asynchronous.

User request НЕ трябва да чака email provider-а.

==================================================
34. STORAGE
===========

Supabase Storage:

* user avatars
* playlist covers
* user-generated images
* application-owned assets

НЕ сваляй и НЕ съхранявай:

* TIDAL audio
* YouTube audio
* external copyrighted audio

Не mirror-вай външен music catalog.

==================================================
35. SECURITY
============

Задължително:

* Spring Security
* JWT validation
* authorization
* ownership checks
* rate limiting
* validation
* CORS
* secure headers
* secret management
* database least privilege
* audit logging
* structured error handling

Никога не вярвай на client-supplied:

userId
ownerId
private playlist ownership
friendship ownership

Derive identity from authenticated JWT.

==================================================
36. PROVIDER ABSTRACTION
========================

Създай отделни abstraction-и:

MusicMetadataProvider

MusicSearchProvider

PlaybackProvider

External provider implementations:

TidalMetadataProvider
YouTubePlaybackResolver

Business domain code не трябва да съдържа TIDAL-specific или YouTube-specific logic.

В бъдеще трябва да можем да добавим:

NewMetadataProvider
NewPlaybackProvider

без да преработваме:

* Playlist Service
* Social Service
* Activity Service
* Recommendation Service

==================================================
37. TIDAL COMPLIANCE GATE
=========================

TIDAL integration трябва да бъде behind provider adapter.

Преди production launch трябва да бъде проверено:

* какво metadata можем да получаваме
* какво можем да съхраняваме
* за какъв период
* какво можем да показваме
* какви са ограниченията за комбинация с други providers
* дали архитектурата TIDAL metadata -> YouTube playback е допустима
* дали е необходимо explicit approval

НЕ приемай автоматично, че тази архитектура е позволена само защото API-то позволява технически заявката.

Добави configuration flags:

music.providers.tidal.enabled=true
music.providers.youtube.enabled=true
music.providers.crossProviderPlayback.enabled=false

По подразбиране:

crossProviderPlayback = false

Тази функционалност се активира само след проверка на актуалните provider terms.

==================================================
38. YOUTUBE PLAYBACK
====================

YouTube трябва да бъде използван чрез допустимия официален player integration.

НЕ прави:

YouTube
->
download audio
->
R2
->
our <audio>

НЕ извличай директни audio URLs.

Нашият backend съхранява YouTube video identifier / playback source metadata.

Frontend player използва подходящия YouTube playback mechanism.

==================================================
39. PLAYER
==========

Music player-ът е first-class frontend feature.

Desktop:

persistent bottom player

Mobile:

compact player
+
expandable full-screen player

Capabilities:

* play
* pause
* previous
* next
* seek
* volume
* mute
* shuffle
* repeat
* queue
* current track
* progress
* artwork
* artist
* track title
* liked state
* add to playlist
* share

Frontend не трябва да знае matching algorithm-а.

Frontend получава internal PlaybackSource.

Пример:

{
trackId,
provider,
providerResourceId,
playbackType
}

Playback adapter управлява provider-specific mechanics.

==================================================
40. FRONTEND ARCHITECTURE
=========================

Feature-oriented organization.

Пример:

features/
auth/
music/
player/
library/
playlists/
social/
recommendations/
activity/
notifications/

Избягвай един гигантски components/ folder.

Use:

* Server Components по подразбиране
* Client Components само когато е необходимо
* TanStack Query за server state
* Zustand само за подходящ global client state

==================================================
41. ОСНОВНИ СТРАНИЦИ
====================

/login
/register
/forgot-password

/home

/search
/search/[query]

/artists/[artistId]

/albums/[albumId]

/tracks/[trackId]

/playlists/[playlistId]

/library

/liked

/recently-played

/friends

/friends/requests

/profile/[username]

/notifications

/settings

==================================================
42. HOME PAGE
=============

Home трябва да изглежда като истинска music platform.

Секции:

* Good morning
* Recently played
* Made for you
* Recommended for you
* Trending among friends
* New releases
* Because you listened to...
* Friends are listening to...
* Recommended playlists

В бъдеще това трябва да бъде personalized feed.

==================================================
43. SOCIAL EXPERIENCE
=====================

Friends:

* friend requests
* friends
* suggestions

Profile:

* avatar
* name
* username
* bio
* currently listening
* recent activity
* playlists
* liked/public music, според privacy settings

Activity:

"Иван слуша Radiohead — Nude"

или:

"Мария създаде playlist Coding Sessions"

==================================================
44. PLAYLISTS
=============

Playlist page трябва да съдържа:

* artwork
* title
* description
* owner
* track list
* total duration
* play
* shuffle
* save
* share

Track operations:

* play
* add queue
* like
* remove
* reorder

Подготви архитектурата за collaborative playlists в бъдеще.

==================================================
45. VISUAL DESIGN
=================

Дизайнът трябва да бъде:

* dark
* premium
* serious
* modern
* minimal
* elegant
* music-focused

Основна визуална идентичност:

DEEP NAVY
+
BLACK
+
WHITE
+
DARK BLUE GRADIENTS

Основният gradient:

deep navy blue
->
dark rich blue
->
black

Основен background:

near-black / very dark navy.

Основен text:

white

Secondary text:

cool gray

Borders:

subtle dark gray / dark navy

Accent:

deep blue

Градиентите се използват основно за:

* hero sections
* artist pages
* album hero
* active player
* highlighted recommendations
* selected states

НЕ използвай:

* Spotify green
* TIDAL visual identity
* прекомерно purple
* прекомерен neon/cyberpunk
* детски цветове
* прекомерен glassmorphism
* прекалено заоблен UI

Визуалният стил трябва да изглежда като:

premium music + technical + serious + modern.

==================================================
46. RESPONSIVE DESIGN
=====================

Desktop:

Sidebar
+
Main content
+
Persistent player

Tablet:

Collapsible sidebar

Mobile:

Bottom navigation
+
Compact player
+
Expandable full player

Приложението трябва да е mobile-first и responsive.

==================================================
47. OBSERVABILITY
=================

Всеки Spring Boot service трябва да съдържа:

Spring Boot Actuator
Micrometer
OpenTelemetry
structured JSON logging
health checks
metrics

Следи:

API latency
p95
p99
error rate
DB latency
Redis latency
RabbitMQ queue depth
TIDAL latency
TIDAL errors
YouTube latency
YouTube quota
YouTube matching success
YouTube matching confidence
Realtime usage
Recommendation latency

Добави correlation ID към request-а.

Trace:

Frontend
->
Gateway
->
Service
->
Database / Provider
->
Event Bus
->
Consumer

трябва да може да бъде проследен.

==================================================
48. RESILIENCE
==============

External providers могат да падат.

Използвай:

* timeout
* retries
* exponential backoff
* circuit breaker
* bulkhead
* rate limit
* fallback

Ако TIDAL е unavailable:

cached/local metadata може да продължи да работи.

Ако YouTube е unavailable:

track-ът трябва да остане достъпен в catalog-а, но playback може временно да бъде unavailable.

Ако Realtime е unavailable:

постоянните функции трябва да продължат да работят.

Ако Resend е unavailable:

email трябва да бъде retry-нат asynchronous.

==================================================
49. API DESIGN
==============

REST APIs.

Всички публични APIs:

/api/v1/...

Използвай:

* OpenAPI
* consistent errors
* cursor pagination където е необходимо
* idempotency за подходящите операции
* validation
* request IDs

Error example:

{
"code": "TRACK_NOT_FOUND",
"message": "Track was not found",
"traceId": "..."
}

Не връщай stack traces към клиента.

==================================================
50. LOCAL DEVELOPMENT
=====================

Създай Docker Compose environment.

Инфраструктура:

* Redis
* RabbitMQ
* backend services
* frontend

За Supabase използвай подходящия local development модел.

Provider credentials трябва да идват от environment variables.

Създай:

.env.example

Никога не commit-вай secrets.

==================================================
51. REPOSITORY STRUCTURE
========================

Monorepo:

/
apps/
web/

services/
api-gateway/
identity-social-service/
music-catalog-service/
playback-resolver-service/
library-playlist-service/
recommendation-service/
activity-notification-service/

packages/
api-contracts/
event-contracts/
shared-types/
shared-config/

infra/
docker/
render/
cloudflare/
observability/

docs/
architecture/
adr/
api/
development/

Не създавай огромен shared package с business logic.

Shared packages могат да съдържат само:

* API contracts
* event contracts
* infrastructure utilities
* configuration
* truly shared primitives

НЕ споделяй domain logic между services.

==================================================
52. DOCUMENTATION
=================

Преди сериозна имплементация създай:

docs/architecture/overview.md
docs/architecture/microservices.md
docs/architecture/data-ownership.md
docs/architecture/events.md
docs/architecture/realtime.md
docs/architecture/music-providers.md
docs/architecture/youtube-matching.md
docs/architecture/security.md
docs/architecture/deployment.md
docs/architecture/scaling.md

Създай ADRs:

ADR-001 Spring Boot microservices
ADR-002 Single Supabase project
ADR-003 PostgreSQL logical ownership
ADR-004 TIDAL provider abstraction
ADR-005 YouTube playback resolution
ADR-006 Realtime strategy
ADR-007 RabbitMQ
ADR-008 Redis caching
ADR-009 Render backend deployment
ADR-010 Cloudflare frontend deployment
ADR-011 Better Auth as identity provider (replaces Supabase Auth, 2026-09-18)

==================================================
53. DEPLOYMENT НА BACKEND-A В RENDER
====================================

Всеки Spring Boot microservice трябва да бъде deployable като независим Render service.

Render configuration трябва да бъде:

* environment-based
* secret-based
* container friendly
* health-checkable
* observable

Всеки service трябва да има:

/actuator/health

който може да бъде използван от Render health checks.

Не използвай local filesystem като persistent storage.

Не разчитай на in-memory state между instances.

Всички persistent данни трябва да бъдат в:

* PostgreSQL
* Redis
* Supabase Storage
* RabbitMQ

според use case-а.

==================================================
54. FRONTEND DEPLOYMENT В CLOUDFLARE
====================================

Next.js deployment трябва да бъде оптимизиран за Cloudflare.

Cloudflare трябва да управлява:

* DNS
* CDN
* edge layer
* frontend deployment
* caching, когато е подходящо
* security / WAF, когато е необходимо

Frontend не трябва да съдържа secrets за backend providers.

Browser комуникацията:

Browser
|
v
Cloudflare
|
v
Next.js
|
v
Backend API

==================================================
55. DOMAIN
==========

Domain registrar:

Spaceship

DNS:

Cloudflare

Domain архитектурата трябва да позволява например:

app.example.com
api.example.com

или подобна production-ready структура.

==================================================
56. FIRST VERTICAL SLICE
========================

НЕ имплементирай целия продукт наведнъж.

Първо реализирай един напълно работещ end-to-end vertical slice:

Register
->
Login
->
Profile
->
Search Track
->
TIDAL metadata
->
Canonical Track
->
YouTube candidate resolution
->
Matching algorithm
->
Playback source
->
Player
->
Listening event
->
Realtime currently listening
->
Activity

Този flow трябва да бъде production-quality.

След това:

Friends
->
Likes
->
Playlists
->
Activity
->
Notifications
->
Recommendations

==================================================
57. TESTING
===========

Всеки service:

* unit tests
* integration tests
* API tests
* contract tests

Особено висок test coverage за:

* authorization
* friendships
* playlists
* catalog normalization
* provider adapters
* YouTube matcher
* event consumers
* recommendation logic

YouTube matcher трябва да има fixtures за:

* official audio
* official music video
* lyric video
* cover
* live
* remix
* slowed
* reverb
* instrumental
* karaoke
* sped up
* nightcore
* multiple candidates with identical duration
* featured artists
* punctuation differences
* Unicode normalization
* artist aliases

==================================================
58. SCALING
===========

Най-натоварени вероятно ще бъдат:

1. music search
2. playback resolution
3. home feed
4. recommendation feed
5. Realtime presence
6. activity feed

Scale services независимо.

Catalog:

read-heavy

Playback Resolver:

cache-heavy

Activity:

append-heavy

Recommendation:

asynchronous

Presence:

ephemeral

==================================================
59. FUTURE PROVIDER REPLACEMENT
===============================

Архитектурата трябва да позволява:

TIDAL
->
another metadata provider

YouTube
->
another playback source provider

RabbitMQ
->
Kafka или друга система

PostgreSQL
->
another PostgreSQL cluster

без цялостно пренаписване на domain layer-а.

==================================================
60. ОСНОВЕН АРХИТЕКТУРЕН ПРИНЦИП
================================

Когато се избира между:

повече microservices
и
по-ясни bounded contexts,

избирай bounded contexts.

Когато се избира между:

повече infrastructure
и
по-малко operational complexity,

избирай по-малко infrastructure.

Когато се избира между:

provider-specific code
и
provider abstraction,

избирай abstraction на integration boundary.

Когато се избира между:

real-time навсякъде
и
real-time само там, където носи реална полза,

избирай второто.

Когато се избира между:

distributed complexity
и
простота,

избирай простотата, освен ако distributed architecture носи реална стойност.

==================================================
61. ПЪРВОНАЧАЛНА АРХИТЕКТУРА
============================

Цялата система концептуално:

```
                     USERS
                       |
                       v
                  Cloudflare
                       |
           +-----------+-----------+
           |                       |
           v                       v
       Next.js                 API Gateway
       Cloudflare             Spring Boot
                                   |
      +------------+---------------+------------------+
      |            |               |          |       |
      v            v               v          v       v
   Social       Catalog        Playback    Library  Activity
   Service     Service        Resolver    Service   Service
      |            |               |          |       |
      |            |               |          |       |
      +------------+---------------+----------+-------+
                       |
                       v
                   PostgreSQL
                    Supabase
                       |
      +----------------+----------------+
      |                |                |
      v                v                v
    Auth           Realtime          Storage
```

External systems:

Catalog:
Spring Boot
->
TIDAL

Playback resolution:
Spring Boot
->
YouTube API

Email:
Spring Boot
->
Resend

Async:
Spring Boot
->
RabbitMQ

Cache:
Spring Boot
->
Redis

Backend deployment:
Render

Frontend:
Cloudflare

==================================================
62. КРАЕН ПРОДУКТОВ ПРИНЦИП
===========================

Приложението трябва да се усеща като:

Spotify/TIDAL-quality music experience
+
social network
+
modern discovery engine

но да има собствена техническа и визуална идентичност.

Потребителят не трябва да се интересува от:

* кой provider ни дава metadata
* как намираме YouTube video
* къде се пази cache
* кой microservice обработва request-а

За потребителя това трябва да е:

едно бързо,
модерно,
premium,
социално music приложение.

==================================================
63. ПОРЕДНОСТ НА РАБОТА
=======================

Следвай следната последователност:

1. Анализирай repository-то.
2. Създай architecture документацията.
3. Дефинирай bounded contexts.
4. Дефинирай database ownership.
5. Дефинирай domain entities.
6. Дефинирай API contracts.
7. Дефинирай domain events.
8. Създай monorepo структура.
9. Настрой Spring Boot services.
10. Настрой Supabase integration.
11. Настрой Redis.
12. Настрой RabbitMQ.
13. Настрой observability.
14. Имплементирай vertical slice.
15. Напиши тестове.
16. Провери end-to-end flow.
17. След това продължи с останалите bounded contexts.

Не генерирай огромно количество код предварително.

Работи incremental.

След всяка голяма architectural стъпка:

* проверявай build
* проверявай tests
* проверявай contracts
* проверявай security assumptions
* проверявай dependency boundaries

Когато има архитектурна несигурност, предпочитай решение, което запазва възможността provider/service да бъде заменен по-късно.

Крайната цел е production-grade система с ясни boundaries, добра observability, добра resilience и минимална ненужна complexity.
