# PRODUCT.md

Technical documentation for the Loli Daily Muzei art source.

## Project overview

Android app (Kotlin + Jetpack Compose + Material 3) that acts as a [Muzei Live Wallpaper](https://muzei.co/) art source, pulling daily artwork from the [Loli Commons API](https://loliconey.tsuki.ga/). Users can react to artwork via [Bangumi](https://bgm.tv/) OAuth, bookmark favorites, submit new artwork, and customize the theme with dynamic color extraction.

- Package: `me.eroi.lolidaily.muzei`
- Min SDK 28, Target/Compile SDK 37, JVM 17, Kotlin 2.3.21
- Single module: `:app`

## Architecture

Six Activities, three BroadcastReceivers, three ContentProviders, and a WorkManager Worker form the core:

### Activities

- **`MainActivity`** (Compose, 4-tab NavigationBar/NavigationRail: Today / Bookmarks / Submit / Settings) — tag filter, cached gallery with reactions, Bangumi login, theme selector, bookmark management, artwork submission.
- **`LoginActivity`** — WebView-based Loli Commons OAuth; extracts JWT session from redirect URL.
- **`PixivLoginActivity`** — WebView-based Pixiv account login (`PHPSESSID` cookie) for accessing original quality images.
- **`AboutActivity`** — app info, links, FAQ screen.
- **`AdditionalSettingsActivity`** — developer tools and testing options.
- **`AcknowledgmentsActivity`** — third-party services and libraries credits.

### Muzei integration

- **`LoliDailyArtProvider`** (`MuzeiArtProvider`) — Muzei's entry point. `onLoadRequested` enqueues the Worker and ensures daily refresh is scheduled. Provides command actions (View Source / View Artist / Force Refresh). `openFile` serves cached images via `FileProvider`.
- **`LoliDailyArtWorker`** (`WorkManager` `Worker`) — central orchestrator. Fetches daily JSON via `LoliApiClient`, downloads images via `ImageDownloader`, persists metadata to Room via `EntityMapper`, filters by user's selected tags, and pushes to Muzei via `ProviderClient.setArtwork()`.
- **`DocumentsProvider`** — allows Muzei to access artwork files directly.

### Receivers

- **`RefreshReceiver`** — broadcast handler for the Force Refresh command action from Muzei.
- **`DailyRefreshReceiver`** — receives the daily refresh alarm from `AlarmManager`, enqueues a scheduled worker within the configured time window (30 min).
- **`DailyRefreshRescheduleReceiver`** — listens for `BOOT_COMPLETED`, `TIME_CHANGED`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` to recreate the daily refresh alarm.

### Data flow

```
Muzei → LoliDailyArtProvider.onLoadRequested
      → WorkScheduler.ensureDailyRefreshScheduled (AlarmManager)
      → WorkScheduler.enqueueLoad
        → LoliDailyArtWorker.doWork()
          → fetch /api/v1/daily (gated: once per date change, or force-refresh)
          → cache JSON to filesDir/api_cache.json
          → download all images to filesDir/artworks/<md5>.<ext> (regardless of tag filter)
          → persist metadata to Room (cached_artworks table)
          → filter cards by KEY_ENABLED_TAGS
          → ProviderClient.setArtwork(filtered)
          → if new day: broadcast NEXT_ARTWORK to net.nurik.roman.muzei

AlarmManager
  → DailyRefreshReceiver
    → WorkScheduler.enqueueScheduledDailyRefresh
    → DailyRefreshScheduler.scheduleNext (reschedule for tomorrow)
```

## Packages

### `api/`
Network clients and session management. `LoliApiClient` talks to the Loli Commons API (daily feed, reactions, submit, artist resolve). `BangumiApiClient` talks to Bangumi API v0 (topics, replies, character search). `ReactionService` fetches/caches/submits per-artwork emoji reactions. `SessionManager` persists OAuth JWT, Bangumi profile, Pixiv session, LC badge, and domain preference in SharedPreferences.

### `api/link/`
Pluggable source URL parser registry. Each parser (Twitter, Pixiv, Bilibili) handles URL matching, canonical form normalization, image resolution, and artist lookup for one platform. `SourceLinkParserRegistry` dispatches to the right parser and falls back to the Loli Commons API for unknown types. `LinkUtils` handles short link resolution, tracking param stripping, and URL extraction from mixed text.

### `api/decoder/`
Coil `AvifDecoder` — decodes AVIF images via Android `ImageDecoder` (API 31+), detected by magic bytes.

### `db/`
Room database (version 2) with two tables: `cached_artworks` (artwork metadata keyed by MD5 token, complex fields as JSON strings for migration-free schema) and `character_history` (recently selected characters for the submit page). `EntityMapper` converts between API `Card` models and DB entities. `DatabaseProvider` is a thread-safe lazy singleton.

### `model/`
kotlinx-serialization DTOs for all API responses (`DailyResponse`, `Card`, `BangumiTopic`, `BangumiReply`, `LcUserInfo`, `SlimCharacter`, etc.) and the `ArtworkPreview` UI model that combines cached file URI with metadata, reactions, bookmark state, and discussion info.

### `worker/`
Background work infrastructure. `WorkScheduler` enqueues WorkManager requests with network constraints and cooldown. `DailyRefreshScheduler` manages AlarmManager-based daily refresh at a user-configured time (default 07:30 GMT+8, 30-min window). `ImageDownloader` handles image downloads with retry, magic-byte validation, AVIF detection, and stale file cleanup. `ArtworkBuilder` constructs Muzei `Artwork` objects from API data. `EmojiMap` maps Bangumi emoji IDs to local drawable resources.

### `ui/theme/`
Material 3 theming with three axes: `ThemeMode` (SYSTEM/LIGHT/DARK), `ColorSource` (from artwork image / manual picker / default), and `ColorStyle` (9 variants: TONAL_SPOT, VIBRANT, CONTENT, FIDELITY, EXPRESSIVE, MONOCHROME, NEUTRAL, RAINBOW, FRUIT_SALAD). Color transitions are animated via 400ms tween on all M3 color roles.

### `ui/screen/`
Compose screens. `MainScreen` is a 4-tab layout (Today / Bookmarks / Submit / Settings) with NavigationBar on phone and NavigationRail on tablet/folderable. Each tab has its own page composable under `pages/`. Standalone screens exist for About, Additional Settings, and Acknowledgments.

### `ui/screen/components/`
Reusable Compose components: artwork detail sheets, reaction chips, comment threads, character search bar, image picker, fullscreen zoomable viewer, banners (update/setup/battery/submit-tip), settings helpers, color picker, source status card, account card, filter options, smiley mapper.

### `util/`
Standalone utilities: `VersionChecker` (GitHub releases API with 24h cache), `ArtworkColorExtractor` (Palette-based dominant color extraction), `M3SchemeGenerator` (full M3 ColorScheme from source ARGB), `RecentsPrivacy` (hide content from recents screen), `ExportArtwork` (copy to Pictures/LoliDaily via MediaStore), `Md5` (token generation).

## Key design decisions

- **Tokens are MD5 of `card.imgUrl`**. This is how Artwork rows, cached files, Room entities, and reaction lookups are all keyed.
- **All images downloaded, filtering at push time**. Tag filter is applied when pushing to Muzei, so changing filters only requires a re-filter (no network call).
- **Room stores complex fields as JSON strings** (`characters`, `suggestedBy`) to keep the schema flat and migration-free.
- **`LoliDailyArtWorker.companion` is the public API surface**. Most callers go through static methods on the companion object which delegate to `WorkScheduler`, `ReactionService`, `SessionManager`.
- **Daily refresh is scheduled via AlarmManager in GMT+8** (default 07:30 ± 30min window). `DailyRefreshScheduler` computes the next trigger epoch and reschedules after each fire.
- **Cooldown enforcement**: 10s between worker enqueues, 1-min between reaction fetches.
- **Pixiv images resolved via hidden WebView**. Pixiv's API is behind Cloudflare which blocks OkHttp (TLS fingerprint mismatch), but WebView shares the browser's TLS stack and cookies.
- **Bilibili images resolved via WebView** to bypass captcha protection.
- **Source link parsers are pluggable**. New platforms are added by implementing `SourceLinkParser` and registering in `SourceLinkParserRegistry`.
- **Theme color transitions are animated**. All M3 color roles animate with 400ms tween when the color source or style changes.
- **Version checking against GitHub releases** with 24-hour cache to avoid excessive API calls.
