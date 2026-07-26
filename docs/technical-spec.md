# Technical Specification

## Tech Stack

| Layer | Technology | Version (approx) |
|---|---|---|
| Language | Kotlin | 2.1.0 |
| UI Framework | Jetpack Compose | BOM 2024.12+ |
| Min SDK | API 26 (Android 8.0) | ~95% device coverage |
| Target SDK | API 35 (Android 15) | Latest stable |
| HTTP Client | Retrofit 2 + OkHttp | 2.11+ / 4.12+ |
| JSON Parsing | Kotlinx Serialization | 1.7+ |
| Image Loading | Coil 3 (Compose) | 3.0+ |
| Video Playback | Media3 ExoPlayer | 1.5.1 |
| Color Extraction | AndroidX Palette | 1.0+ |
| Animation | Compose Animation Core | (BOM) |
| Local Storage | DataStore (Preferences) | 1.1+ |
| Media Cache DB | Room | 2.7.1 |
| Background Sync | WorkManager | 2.9.1 |
| Credential Storage | EncryptedSharedPreferences (Tink) | 1.1+ |
| Dependency Injection | Hilt | 2.52+ |
| Worker Injection | Hilt-Work | 1.2.0 |
| Code Formatting | Spotless + ktlint | 7.0.2 / 1.4.1 |
| Build System | Gradle Kotlin DSL | 8.10.2 (AGP 8.7.3) |
| JDK | OpenJDK 17 | Required for builds |

### Suppressed ktlint rules

- `ktlint_standard_no-wildcard-imports` — standard Compose convention
- `ktlint_standard_function-naming` — Compose `@Composable` functions use
  PascalCase, which violates the default rule

## Architecture Layers

```
immich-android/
├── app/
│   ├── src/main/java/com/dav3/immichframe/
│   │   ├── data/
│   │   │   ├── remote/          # Retrofit API interfaces, DTOs
│   │   │   │   ├── ImmichApi.kt       # Immich endpoints
│   │   │   │   ├── GitHubApi.kt       # GitHub releases API (self-update)
│   │   │   │   ├── Dtos.kt            # Immich DTOs
│   │   │   │   ├── GitHubDtos.kt      # GitHub DTOs
│   │   │   │   └── ImmichRepositoryImpl.kt
│   │   │   ├── local/           # DataStore, EncryptedPrefs, Room cache
│   │   │   │   ├── DataStoreProvider.kt  # Shared DataStore singleton
│   │   │   │   ├── SettingsRepositoryImpl.kt
│   │   │   │   ├── MediaCacheDatabase.kt # Room DB (cached_assets, album_sync_states)
│   │   │   │   ├── MediaCacheDao.kt      # Room DAOs
│   │   │   │   ├── MediaCacheEntities.kt # Room entities
│   │   │   │   ├── MediaCacheRepositoryImpl.kt
│   │   │   │   └── Converters.kt         # Room type converters
│   │   │   ├── sync/            # WorkManager background sync
│   │   │   │   ├── MediaCacheWorker.kt   # Downloads + reconciles album assets
│   │   │   │   └── SyncScheduler.kt      # Periodic/one-time sync scheduling
│   │   │   ├── update/          # Self-update logic
│   │   │   │   └── UpdateManager.kt
│   │   │   └── (repository/ is in di/)
│   │   ├── domain/
│   │   │   ├── model/           # Domain models (Album, Asset, Settings)
│   │   │   ├── repository/      # Repository interfaces
│   │   │   ├── system/          # AutostartPermissions.kt, LauncherHelper.kt
│   │   │   └── sync/            # MediaCacheWorker, SyncScheduler
│   │   ├── di/                  # Hilt modules
│   │   ├── ui/
│   │   │   ├── setup/           # Setup screen (URL + API key)
│   │   │   ├── albums/          # Album picker
│   │   │   ├── slideshow/       # Slideshow player (images, video, clock)
│   │   │   ├── settings/        # Settings screen
│   │   │   ├── update/          # Update dialog + ViewModel
│   │   │   ├── nav/             # Navigation graph
│   │   │   └── theme/           # Material 3 theme
│   │   ├── BootReceiver.kt      # BOOT_COMPLETED → launch slideshow (guards startActivity with SYSTEM_ALERT_WINDOW check)
│   │   ├── ImmichFrameApp.kt    # Application class (@HiltAndroidApp)
│   │   └── MainActivity.kt      # Single activity (also target of LauncherAlias)
│   ├── src/main/res/
│   │   └── xml/file_paths.xml   # FileProvider config for APK install
│   └── build.gradle.kts
├── docs/                        # This documentation
├── .github/workflows/           # dev-build.yml, prod-build.yml
├── build.gradle.kts             # Root build file
├── settings.gradle.kts
└── gradle/libs.versions.toml    # Version catalog
```

## Data Flow

```
UI (Compose) → ViewModel → Repository → Retrofit → Immich API
                              ↕
                           DataStore (settings, credentials, album selection)
```

- **ViewModel** holds UI state as `StateFlow`, survives config changes.
- **Repository** abstracts data sources (remote API + local storage).
- **Coil** handles image fetching/caching transparently — the slideshow
  feeds Coil image URLs and Coil manages the disk/memory cache.
- **ExoPlayer (Media3)** handles video playback inline within the slideshow
  for video assets (when Skip Videos is off).
- **Palette API** extracts dominant color from each image for adaptive
  background (letterbox fill).
- **Retrofit OkHttp interceptor** injects the `x-api-key` header on every
  Immich API call automatically.

## Package Naming

Application ID: `com.dav3.immichframe`

## Image Caching Strategy

Coil manages an LRU memory cache and a disk cache automatically.

Configuration:
- **Memory cache**: 25% of available app memory (Coil default)
- **Disk cache**: 500 MB (configurable), stores preview-quality images
- **Prefetch**: Slideshow prefetches the next 3 images ahead of the current one
- **Image size**: Request preview thumbnails (`size=preview` in Immich API)
  for slideshow display, not full originals — saves bandwidth and disk

### Auth for image/video URLs

Image and video URLs are constructed with the API key appended as a query
parameter (`?apiKey=<key>`) for Coil and ExoPlayer to fetch without custom
HTTP clients. API calls (Retrofit) use the `x-api-key` header via an
OkHttp interceptor instead.

> **Note**: Immich v3 deprecated the `apiKey` query parameter in favor of
> `key`. The app currently uses `apiKey` for image/video URLs and may need
> to switch to `key` if the Immich server stops accepting the old param.
> See [api-reference.md](api-reference.md) for details.

## Media Cache (Room + WorkManager)

The app maintains a local Room database (`media_cache_db`) that stores
downloaded copies of album assets for offline-capable, instant slideshow
loading.

### Database schema

- **`cached_assets`** — one row per downloaded asset: `id`, `album_id`,
  `type` (IMAGE/VIDEO), `file_path`, `thumbnail_path`, `file_size`,
  `checksum`, `last_modified`, `cached_at`. Indexed on `album_id`,
  `cached_at`, `last_modified`.
- **`album_sync_states`** — per-album sync metadata: `album_id` (PK),
  `last_synced_at`, `last_cursor`, `asset_count`.

### Sync lifecycle

1. **On slideshow load**: the ViewModel first checks the cache. If cached
   assets exist, they're displayed immediately (offline-capable). If
   `autoSync` is on, a one-time `MediaCacheWorker` is enqueued to
   reconcile the cache against the server.
2. **Periodic sync**: `SyncScheduler` enqueues a periodic
   `MediaCacheWorker` (minimum interval 15 min, enforced by WorkManager)
   that fetches album asset lists, downloads new/updated assets, and
   removes deleted ones.
3. **Worker logic** (`MediaCacheWorker.performFullSync`):
   - Fetches remote asset list for each album via `POST /search/metadata`
   - Deletes cached assets no longer in the remote album
   - Downloads new/updated assets (original + thumbnail) via OkHttp
   - Updates `AlbumSyncState` with sync timestamp + asset count
   - Reports progress via `SyncProgress` StateFlow

Cache files are stored in `getExternalFilesDir("media_cache")`.

### WorkManager initialization

`ImmichFrameApp` implements `Configuration.Provider` and provides a
`HiltWorkerFactory` so that `MediaCacheWorker` can receive its
dependencies via Hilt. The default `WorkManagerInitializer` is removed
in `AndroidManifest.xml` (via `tools:node="remove"`) to avoid the
duplicate-initialization crash.

## Security

- API key stored via `EncryptedSharedPreferences` (AES-256, backed by Android Keystore)
- API key never logged, never sent to any endpoint except the user's Immich server
- Server URL stored in DataStore (not sensitive)
- No telemetry, no analytics, no third-party tracking
- The API key is only sent to `api.github.com` when checking for updates
  (no key sent — just a public GET to the releases endpoint)

## Navigation

Single-activity architecture with Compose Navigation:

```
Setup → Albums → Slideshow
                ↕
             Settings
```

- `Setup` is the start destination when no credentials are stored.
- Once credentials + album selection exist, start destination is `Slideshow`.
- `Settings` is accessible from `Slideshow` and `Albums`.

## State Persistence

| Data | Storage | Key | Type |
|---|---|---|---|
| Server URL | DataStore | `server_url` | String |
| API Key | EncryptedSharedPreferences | `api_key` | String (encrypted) |
| Selected Album IDs | DataStore | `selected_album_ids` | String set |
| Slideshow interval | DataStore | `interval_sec` | Int (5–120) |
| Transition duration | DataStore | `transition_sec` | Float (0–3) |
| Image fill mode | DataStore | `fill_mode` | String enum (CONTAIN/COVER) |
| Show clock | DataStore | `show_clock` | String bool |
| Clock size | DataStore | `clock_size` | Float (24–96 sp) |
| Clock X position | DataStore | `clock_x` | Float (0.0–1.0 normalized, -1 = default) |
| Clock Y position | DataStore | `clock_y` | Float (0.0–1.0 normalized, -1 = default) |
| Clock snap to grid | DataStore | `clock_snap_to_grid` | String bool |
| Keep screen on | DataStore | `keep_screen_on` | String bool |
| Fullscreen | DataStore | `fullscreen` | String bool |
| Shuffle | DataStore | `shuffle` | String bool |
| Skip videos | DataStore | `skip_videos` | String bool |
| Muted | DataStore | `muted` | String bool |
| Start on boot | DataStore | `start_on_boot` | String bool |
| Launcher mode | DataStore | `launcher_mode` | String bool (enables the Home activity-alias) |
| Boot verified | DataStore | `boot_verified` | String bool (self-test: BootReceiver sets true on successful fire) |
| Auto-update | DataStore | `auto_update` | String bool |
| Adaptive background | DataStore | `adaptive_background` | String bool |
| Photo animations | DataStore | `photo_animations` | String bool |
| Anim: Zoom In | DataStore | `anim_zoom_in` | String bool |
| Anim: Zoom Out | DataStore | `anim_zoom_out` | String bool |
| Anim: Pan Left | DataStore | `anim_pan_left` | String bool |
| Anim: Pan Right | DataStore | `anim_pan_right` | String bool |
| Anim: Pan Up | DataStore | `anim_pan_up` | String bool |
| Anim: Pan Down | DataStore | `anim_pan_down` | String bool |
| Auto Sync | DataStore | `auto_sync` | String bool (default true) |
| Sync Interval | DataStore | `sync_interval_minutes` | Int (1 or 5–480 step 5, default 30) |

All settings flow through a single shared DataStore instance
(`DataStoreProvider.kt`) — there must be only one DataStore active per file
or Android throws `IllegalStateException`.

## Build Configuration

- **Debug builds**: `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-dev"`,
  signed with a shared debug keystore (so all dev builds share the same signature
  for clean upgrades over each other).
- **Release builds**: R8 minification + resource shrinking, signed with the
  production keystore.
- **`BuildConfig.GIT_SHA`**: injected at build time via `git rev-parse HEAD`,
  used by the self-update feature to compare against GitHub release tag SHAs.

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | API calls to Immich server |
| `ACCESS_NETWORK_STATE` | Network connectivity checks |
| `RECEIVE_BOOT_COMPLETED` | Start-on-boot feature |
| `REQUEST_INSTALL_PACKAGES` | Self-update via GitHub releases (APK install) |
| `SYSTEM_ALERT_WINDOW` | Background Activity Launch exemption — required on Android 10+ (API 29+) for `BootReceiver` to call `startActivity()` from a `BOOT_COMPLETED` broadcast. Without it the OS silently blocks the launch. |

## Localization

The app is localized into 13 languages. String resources live in
`app/src/main/res/values*/strings.xml`.

| Locale | Directory |
|---|---|
| English (default) | `values/` |
| Arabic | `values-ar/` |
| Chinese (Simplified) | `values-zh-rCN/` |
| Dutch | `values-nl/` |
| French | `values-fr/` |
| German | `values-de/` |
| Italian | `values-it/` |
| Japanese | `values-ja/` |
| Korean | `values-ko/` |
| Polish | `values-pl/` |
| Portuguese | `values-pt/` |
| Russian | `values-ru/` |
| Spanish | `values-es/` |

`MissingTranslation` lint is disabled to allow incremental localization —
new strings fall back to English until translated.
