# AGENTS.md — muzei-loli-daily

## Project Identity

**muzei-loli-daily** is a [Muzei](https://github.com/romannurik/muzei) wallpaper plugin for Android that fetches daily artwork from the [Lolicommons](https://loliconey.tsuki.ga) collection and serves it to the Muzei Live Wallpaper app.

- **Package:** `me.eroi.lolidaily.muzei`
- **Language:** Kotlin 2.0.21
- **Min SDK:** 24 | **Target SDK:** 35 | **Compile SDK:** 35
- **AGP:** 8.13.2
- **JVM Target:** 17

---

## Architecture

```
Muzei App (host)
    │
    │ discovers via <intent-filter>
    ▼
┌──────────────────────────────────────────┐
│         LoliDailyArtProvider             │  ← MuzeiArtProvider (ContentProvider)
│  - Registers Muzei command actions       │
│  - Serves cached images via openFile()   │
│  - Triggers Worker on load request       │
└──────────────────────────────────────────┘
    │
    │ enqueues via WorkManager
    ▼
┌──────────────────────────────────────────┐
│         LoliDailyArtWorker               │  ← WorkManager Worker
│  - Fetches API JSON (daily cards)        │
│  - Downloads all images (full cache)     │
│  - Records per-image API dates           │
│  - Filters by user tag preferences       │
│  - Pushes matching artwork to Muzei      │
│  - Supports refilter-only mode (no net)  │
└──────────────────────────────────────────┘
    │
    │ reads SharedPreferences
    ▼
┌──────────────────────────────────────────┐
│          SettingsActivity                │  ← AppCompatActivity + Compose
│  - setContent { LoliDailyTheme { } }    │
│  - Tag filter (radio buttons)            │
│  - Cached image gallery preview          │
│  - FAB refresh trigger                   │
└──────────────────────────────────────────┘

            RefreshReceiver               ← BroadcastReceiver (force-refresh)
```

### Key Data Flow

1. **API → Worker**: `LoliDailyArtWorker.doWork()` calls `GET https://loliconey.tsuki.ga/api/v1/daily?badge=LC%20YJ-ES-NC-PG`
2. **Download All**: Worker downloads *all* images regardless of tag filter — the full daily batch is cached locally.
3. **Tag Filter at Push Time**: Worker reads `SharedPreferences("lolidaily_prefs")` → key `enabled_tags` (`Set<String>`). Filters `Card` list by `card.tags` when pushing to Muzei.
4. **Caching**: Images saved to `filesDir/artworks/{md5(url)}.{ext}`. API date check skips re-download when unchanged. JSON response cached to `filesDir/api_cache.json`.
5. **Refilter Mode**: When user changes tags, `enqueueRefilter()` runs Worker with cached data only (no network). Applies new filter and pushes to Muzei immediately.
6. **Muzei Integration**: Worker calls `ProviderContract.getProviderClient().setArtwork(artworks)` to replace the Muzei queue. Sends `NEXT_ARTWORK` broadcast on new daily batch.
7. **Hourly Throttle**: API called at most once per hour unless force-refresh.
8. **Per-Image Dates**: `recordImageDates()` stores `md5(imgUrl) → apiDate` map in SharedPreferences (`image_dates` key). Gallery reads this to display API dates per artwork.

---

## File Map

```
app/src/main/
├── AndroidManifest.xml                               ← All components + INTERNET permission
├── java/me/eroi/lolidaily/muzei/
│   ├── LoliDailyArtWorker.kt                         ← Core: API fetch, image download, cache, filter, push
│   ├── LoliDailyArtProvider.kt                       ← Muzei provider: load trigger, command actions, openFile
│   ├── SettingsActivity.kt                           ← Compose host: state management, prefs read/write, preview builder
│   ├── RefreshReceiver.kt                            ← BroadcastReceiver for force-refresh
│   └── model/
│       ├── ApiModels.kt                              ← @Serializable: DailyResponse, Card, SuggestedBy
│       └── ArtworkPreview.kt                         ← Data class: cached artwork URI + API metadata for gallery display
├── java/me/eroi/lolidaily/muzei/ui/
│   ├── screen/
│   │   └── SettingsScreen.kt                         ← Compose UI: tabbed settings (Gallery + Preference)
│   └── theme/
│       ├── Theme.kt                                  ← LoliDailyTheme (dynamic color on API 31+)
│       ├── Color.kt                                  ← Light/dark color schemes (warm amber-brown)
│       └── Type.kt                                   ← Material3 typography scale
├── res/
│   ├── drawable/
│   │   ├── ic_refresh.xml                            ← Vector: refresh icon
│   │   ├── ic_person.xml                             ← Vector: artist icon
│   │   └── ic_view_source.xml                        ← Vector: external link icon
│   ├── values/
│   │   ├── strings.xml                               ← String resources
│   │   └── themes.xml                                ← AppCompat base theme (no action bar)
│   └── xml/
│       └── file_paths.xml                            ← FileProvider: "artworks/" directory
```

### ArtworkPreview Model

```kotlin
// model/ArtworkPreview.kt
data class ArtworkPreview(
    val uri: Uri,               // FileProvider URI to cached image
    val filename: String,       // e.g. "d41d8cd9.jpg"
    val artistName: String,
    val comment: String,
    val tags: String,
    val characterNames: List<String>,
    val sourceUrl: String,
    val artistUrl: String,
    val date: String,           // API response date (e.g. "2026-04-25")
)
```

Populated in `SettingsActivity.loadPreview()` from cached API response (`api_cache.json`) and per-image date map (`image_dates` prefs key).

---

### SharedPreferences Contract

All keys defined in `LoliDailyArtWorker.companion`. File: `"lolidaily_prefs"` (MODE_PRIVATE).

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled_tags` | `Set<String>` | null / empty | Tag filter; empty = show all |
| `force_refresh` | `Boolean` | false | WorkManager input — bypass API date cache |
| `refilter_only` | `Boolean` | false | WorkManager input — skip network, use cache |
| `last_api_date` | `String` | null | Cached API `date` field (latest batch) |
| `last_fetch_time` | `Long` | 0 | Timestamp of last API call (hourly throttle) |
| `image_dates` | `String` (JSON) | null | JSON `Map<String, String>`: `{md5(imgUrl): "2026-04-25", ...}` — per-image API date |

---

## Dependencies

```kotlin
// build.gradle.kts (app)

// Compose (BOM 2024.12.01)
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.activity:activity-compose:1.9.3")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

// Image loading
implementation("io.coil-kt.coil3:coil-compose:3.0.4")
implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

// Core
implementation("androidx.core:core-ktx:1.15.0")
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("com.google.android.apps.muzei:muzei-api:3.4.2")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("androidx.work:work-runtime-ktx:2.10.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
```

---

## Conventions

### Naming
- Activity/Provider/Worker: `PascalCase.kt`
- Compose screens: `PascalCase.kt` in `ui/screen/`
- Theme files: `PascalCase.kt` in `ui/theme/`
- Resource IDs (strings): `snake_case`
- Vector drawables: `ic_*.xml`
- Constants: `SCREAMING_SNAKE_CASE`

### UI — Compose + Material 3
- Full Compose UI — no XML layouts.
- `LoliDailyTheme` wraps all content. Dynamic color (Material You) on API 31+, warm amber-brown fallback palette.
- `SettingsScreen` is a stateless composable — all state owned by `SettingsActivity`.
- Tabbed layout: `TabRow` + `HorizontalPager` (Gallery tab, Preference tab).
- Image loading: Coil `AsyncImage` with `ImageRequest.Builder(context).data(uri)`.
- `Scaffold` with `TopAppBar` and `FloatingActionButton` (Gallery tab only).
- Gallery: `ArtworkCard` with hero image, date badge overlay, tag badge, artist + comment, detail dialog, fullscreen zoom.

### State Management
- `SettingsActivity`: Direct SharedPreferences + Compose `mutableStateOf`. No ViewModel.
- Worker reads SharedPreferences on each execution. No live push from settings.
- Tag change triggers `enqueueRefilter()` — lightweight, no-network re-push.
- Gallery date display: `SettingsActivity.loadPreview()` builds `ArtworkPreview` from cached files, reads `image_dates` prefs map, falls back to API response `date` field.

### Error Handling
- Worker returns `Result.retry()` on network/parse failures (WorkManager retries with backoff).
- Image download: 3 retries with exponential backoff (1s, 4s, 9s).
- File integrity: magic byte validation for JPEG, PNG, WebP, GIF, BMP.
- Logging via `android.util.Log` with tags: `LoliDailyWorker`, `LoliDailyProvider`, `RefreshReceiver`.
- No user-facing error UI (Toast for refresh confirmation only).

### Muzei Integration
- `LoliDailyArtProvider` extends `MuzeiArtProvider`.
- Command actions: View Source, View Artist (parsed from artwork metadata JSON), Force Refresh.
- `openFile()` serves cached images directly from `filesDir/artworks/`.
- `<meta-data android:name="settingsActivity">` points to `SettingsActivity` — this class name is part of the Muzei contract and must not change.
- Provider authority: `${applicationId}.provider` (resolves to `me.eroi.lolidaily.muzei.provider`).

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Build release (ProGuard enabled)
./gradlew assembleRelease

# Run tests (currently none)
./gradlew test

# Lint
./gradlew lint
```

**Debugging:**
- Log filter: `adb logcat -s LoliDailyWorker:* LoliDailyProvider:* RefreshReceiver:*`
- SettingsActivity is the LAUNCHER activity — can be opened directly from app drawer.
- Requires Muzei app installed on device for wallpaper integration to work.

---

## API Notes

- **Endpoint:** `https://loliconey.tsuki.ga/api/v1/daily?badge=LC%20YJ-ES-NC-PG`
- **Response model:** `DailyResponse { cards: List<Card>, date: String }`
- **Card model:** `Card { imgUrl, tags, artistName, artistUrl, characterNames, characterIds, sourceUrl, comment, suggestedBy }`
- **SuggestedBy model:** `SuggestedBy { nickname, username }`
- **Tag filtering:** Worker checks `card.tags` (single String) against `enabled_tags` Set. Empty set = no filter.
- **Token:** MD5 hash of `imgUrl` — used for deduplication and cache filenames.
- **User-Agent:** `LoliDaily/1.0 (Android)`
- **Hourly throttle:** `last_fetch_time` prefs key; skipped on force-refresh.
- **JSON cache:** Full API response cached to `filesDir/api_cache.json` for refilter-only mode.
- **Per-image dates:** Stored in `image_dates` SharedPreferences key as JSON `Map<String, String>`. Populated by `recordImageDates()` when new daily batch arrives. Gallery falls back to `api_cache.json` date when per-image entry is missing.

---

## Commit Convention

This project follows [Conventional Commits](https://www.conventionalcommits.org/).

### Format

```
<type>(<scope>): <description>

[optional body]
```

### Types

| Type | When |
|---|---|
| `feat` | New feature or functionality |
| `fix` | Bug fix |
| `refactor` | Code restructuring without behavior change |
| `style` | Formatting, whitespace, lint fixes (no logic change) |
| `perf` | Performance improvement |
| `docs` | Documentation only |
| `chore` | Build, deps, tooling, maintenance |
| `test` | Adding or updating tests |

### Scope

Use one of: `worker`, `provider`, `settings`, `ui`, `theme`, `build`, `deps`

### Examples

```
feat(worker): cache all cards and filter by tag at push time
fix(worker): retry failed image downloads with integrity validation
fix(settings): use API response date instead of file hash in gallery overlay
refactor(settings): replace checkboxes with radio buttons in filter tab
style(ui): add swipe-to-switch tabs with HorizontalPager
chore(build): add Compose BOM and Material3 dependencies
docs: add commit convention to AGENTS.md
```

### Rules

- Description must use **imperative mood** ("add", not "added" or "adds")
- First line max **72 characters**
- Separate subject from body with a blank line
- Do **not** end subject with a period
