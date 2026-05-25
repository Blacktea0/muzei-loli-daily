# AGENTS.md

Quick orientation for AI coding agents working in this repo.

## Project overview

Android app (Kotlin + Jetpack Compose + Material 3) that acts as a [Muzei Live Wallpaper](https://muzei.co/) art source, pulling daily artwork from the Loli Commons API (`loliconey.tsuki.ga`). Users can react to artwork via Bangumi (bgm.tv) OAuth.

- Package: `me.eroi.lolidaily.muzei`
- Min SDK 28, Target/Compile SDK 36, JVM 17, Kotlin 2.3.21
- Single module: `:app`
- Detailed reference: `AGENTS.md`

## Build & run

```bash
./gradlew assembleDebug         # build APK
./gradlew installDebug          # build + install to connected device
./gradlew ktlintFormat          # format all Kotlin sources (4-space indent, ktlint_official)
./gradlew ktlintCheck           # check formatting (dry-run, CI)
./gradlew lint                  # static analysis (includes ktlintCheck)
./gradlew build                 # full build
```

A git pre-commit hook runs `ktlintFormat` automatically. There are no tests defined yet.

**Important**: Always use `./gradlew installDebug` to install the APK to a connected device. Do NOT use `adb install` directly, as it may cause signature mismatch issues.

## Mock server

```bash
./gradlew startMockServer       # starts Node.js Express mock at 0.0.0.0:50303
./gradlew stopMockServer        # kills the background server
./gradlew mockLogs              # prints server.log
```

The API server URL is configured at runtime via Debug Settings → API Server. Users can select from known servers (`loliconey.tsuki.ga`, `lc-coney.deno.dev`, `next.bgm.tv`) or enter a custom URL. The default is `https://loliconey.tsuki.ga`. The value is stored in SharedPreferences key `debug_api_base_url`.

## Architecture

Three core components fan out from `LoliDailyArtWorker` (the central orchestrator):

- **`LoliDailyArtProvider`** (`MuzeiArtProvider`) — Muzei's entry point. `onLoadRequested` enqueues the Worker. Provides command actions (View Source / View Artist / Force Refresh).
- **`LoliDailyArtWorker`** (`WorkManager` `Worker`) — fetches daily JSON via `LoliApiClient`, downloads images to `filesDir/artworks/`, persists metadata to Room, filters by user's selected tags, and pushes to Muzei via `ProviderClient.setArtwork()`.
- **`SettingsActivity`** (Compose, 3-tab NavigationBar: Today / Bookmark / Settings) — tag filter, cached gallery with reactions, Bangumi login, theme selector.

Supporting pieces:
- `LoginActivity` — WebView OAuth flow against Bangumi, extracts JWT session from redirect URL
- `RefreshReceiver` — broadcast handler for the Force Refresh command action
- `api/LoliApiClient.kt` — OkHttp client with API endpoint URLs
- `api/ReactionService.kt` — fetches, caches, and submits reactions (1-min cooldown on fetch)
- `api/SessionManager.kt` — persists OAuth JWT session + username in SharedPreferences
- `model/ApiModels.kt` — `DailyResponse`, `Card`, `DailyReactResponse`, etc. (kotlinx-serialization)
- `model/ArtworkPreview.kt` — UI model combining local file URI with API metadata + reactions
- `db/` — Room database (`cached_artworks` table) for persisting artwork metadata across daily rotations
- `worker/WorkScheduler.kt` — enqueues OneTimeWorkRequests with network constraints
- `worker/ImageDownloader.kt` — downloads images with retry, file integrity validation via magic bytes
- `worker/EmojiMap.kt` — maps Bangumi emoji IDs to local `res/drawable/reaction_*.gif` resources

### Data flow

```
Muzei → LoliDailyArtProvider.onLoadRequested
      → LoliDailyArtWorker.enqueueLoad
        → fetch /api/v1/daily (gated: once per date change, or force-refresh)
        → cache JSON to filesDir/api_cache.json
        → download all images to filesDir/artworks/<md5>.<ext> (regardless of tag filter)
        → persist metadata to Room (cached_artworks table)
        → filter cards by KEY_ENABLED_TAGS
        → ProviderClient.setArtwork(filtered)
        → if new day: broadcast NEXT_ARTWORK to net.nurik.roman.muzei
```

### Key design decisions

- **Tokens are MD5 of `card.imgUrl`**. This is how Artwork rows, cached files, and reaction lookups are all keyed.
- **All images downloaded, filtering at push time**. Tag filter is applied when pushing to Muzei, so changing filters only requires a re-filter (no network call).
- **Room stores complex fields as JSON strings** (`characterNames`, `suggestedBy`) to keep the schema flat and migration-free.
- **`LoliDailyArtWorker.companion` is the public API surface**. Most callers go through static methods on the companion object which delegate to the appropriate service class (`WorkScheduler`, `ReactionService`, `SessionManager`).
- **Daily refresh is scheduled in GMT+8** (default 07:30). `WorkScheduler.computeNextRefreshTime()` calculates the next trigger epoch.
- **Cooldown enforcement**: 10s between worker enqueues, 1-min between reaction fetches.

## Conventional Commits

```
<type>[(<scope>)]: <description>
```

Types: `feat`, `fix`, `chore`, `refactor`, `docs`, `style`
Scopes: `settings`, `worker`, `provider`, `auth`, `model`, `ui`, `api`
Keep descriptions lowercase, imperative, no trailing period.
