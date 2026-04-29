# AGENTS.md

Quick orientation for AI coding agents working in this repo.

## What this is

An Android app that plugs into [Muzei Live Wallpaper](https://muzei.co/) as an
art source. It pulls a daily batch of artwork from the Loli Commons API
(`loliconey.tsuki.ga`) and feeds it to Muzei. It also lets the user react to
cards via Bangumi (bgm.tv) OAuth.

- Package: `me.eroi.lolidaily.muzei`
- Min SDK 26, Target/Compile SDK 35, JVM 17
- Kotlin + Jetpack Compose + Material 3
- Single module: `:app`

## Conventional Commits

Follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

```
<type>[(<scope>)]: <description>
```

Types:
- `feat` — new feature
- `fix` — bug fix
- `chore` — build tooling, deps, misc maintenance
- `refactor` — code change that neither fixes a bug nor adds a feature
- `docs` — documentation only
- `style` — formatting, whitespace (no logic change)

Scopes: `settings`, `worker`, `provider`, `auth`, `model`, `ui`, `api`. Omit if
change spans multiple or doesn't fit one.

Keep descriptions lowercase, imperative ("add" not "added"), no period at end.

## Build & run

```bash
./gradlew assembleDebug         # build APK
./gradlew installDebug          # build + install to connected device
./gradlew lint                  # static analysis
./gradlew build                 # full build (no tests defined yet)
```

There is no test source set yet. `local.properties` holds the SDK path and is
git-ignored.

## Mock server

The `mock/` directory contains a Node.js Express server that simulates the Loli
Commons API. It's useful for testing without hitting the real backend.

### Setting the API base URL

The app reads `BuildConfig.API_BASE_URL` at runtime. It is resolved at build
time from the first available source:

1. Env var `LOLI_API_URL`
2. Key `loliApiUrl` in `local.properties`
3. Key `loliApiUrl` in `gradle.properties`
4. Fallback: `https://loliconey.tsuki.ga`

### Running the mock server

```bash
./gradlew startMockServer    # installs npm deps if needed, starts server in background
./gradlew stopMockServer     # kills the background server
./gradlew mockLogs           # prints server.log to console
```

The server binds to `0.0.0.0:50303`. Logs go to `mock/server.log`. The PID is
stored in `mock/.server.pid`.

## Architecture

Three components fan out from `LoliDailyArtWorker`, which is the core:

- `LoliDailyArtProvider` (`MuzeiArtProvider`) — Muzei's entry point. On
  `onLoadRequested` it just enqueues the worker. Also provides per-artwork
  command actions (View Source / View Artist / Force Refresh).
- `LoliDailyArtWorker` (`WorkManager` `Worker`) — fetches the daily JSON,
  downloads images, writes them to `filesDir/artworks/`, filters by user's
  selected tags, and pushes the result to Muzei via
  `ProviderContract.getProviderClient(...).setArtwork(...)`.
- `SettingsActivity` (Compose) — launched from Muzei's source config and the
  app drawer. Tag filter, cached gallery, reaction buttons, Bangumi login.

Supporting pieces:

- `LoginActivity` — WebView OAuth flow against `loliconey.tsuki.ga`.
- `RefreshReceiver` — broadcast handler for the Force Refresh command action.
- `model/ApiModels.kt` — Kotlinx-serializable DTOs for the API.
- `model/ArtworkPreview.kt` — UI model for the settings gallery.

### Data flow

```
Muzei → LoliDailyArtProvider.onLoadRequested
      → LoliDailyArtWorker.enqueueLoad
        → fetch /api/v1/daily (gated to once/hour, bypassed on force-refresh)
        → cache JSON to filesDir/api_cache.json
        → on new daily date: download all images to filesDir/artworks/<md5>.<ext>
        → filter cards by KEY_ENABLED_TAGS
        → ProviderClient.setArtwork(filtered)
        → if new day: broadcast NEXT_ARTWORK to net.nurik.roman.muzei
```

Tokens are MD5 of `card.imgUrl`. That's how `Artwork` rows, cached files, and
reaction lookups are all keyed.

### Tag filter behaviour

All images for the day are downloaded regardless of filter — the filter is
applied at push time. Changing the filter in Settings calls
`enqueueRefilter()`, which re-pushes from cached data without hitting the
network. Tags currently surfaced in UI: `LC0`, `LC YJ`, `LC ES`. The full list
is whatever the API returns; the strings file (`res/values/strings.xml`) only
labels the ones we expose.

### Reactions

Reactions come from `/api/v1/daily/react` and are submitted via
`PATCH /api/v1/daily/react?cardTypeIdx=<index>`. The PATCH API uses card index,
not token — see `getCardIndex(...)`. Auth is a Bearer JWT obtained via the
WebView OAuth flow and persisted in SharedPreferences as
`LoliDailyArtWorker.Session`.

Emoji IDs (e.g. `0`, `54`, `104`...) map to bgm.tv smiley GIFs; we ship local
copies in `res/drawable/reaction_*.gif`. The map lives in
`LoliDailyArtWorker.emojiResId(...)`.

## Reference

### Muzei API

- Official API docs: https://api.muzei.co/
- GitHub repo: https://github.com/muzei/muzei (Apache 2.0)
- MuzeiArtProvider: https://api.muzei.co/reference/com.google.android.apps.muzei.api.provider/-muzei-art-provider/index.html
- ProviderContract: https://api.muzei.co/reference/com.google.android.apps.muzei.api.provider/-provider-contract/index.html
- ProviderClient: https://api.muzei.co/reference/com.google.android.apps.muzei.api.provider/-provider-client/index.html
- Artwork: https://api.muzei.co/reference/com.google.android.apps.muzei.api.provider/-artwork/index.html
- Example sources: https://github.com/muzei/muzei/tree/main/example-unsplash

**Latest**: `com.google.android.apps.muzei:muzei-api:3.4.2` (2024-06-16).
Two API surfaces: **Provider API** (`com.google.android.apps.muzei.api.provider`)
for building sources, **Contract API** (`com.google.android.apps.muzei.api`) for
reading wallpaper state.

### Material Design 3 (Compose)

- M3 design spec: https://m3.material.io/
- Compose M3 developer guide: https://developer.android.com/develop/ui/compose/designsystems/material3
- M3 theming codelab: https://developer.android.com/codelabs/m3-design-theming

Key Compose M3 entry points: `MaterialTheme`, `ColorScheme`, `Typography`,
`Shapes`. Theme builder tools at https://m3.material.io/theme-builder.
