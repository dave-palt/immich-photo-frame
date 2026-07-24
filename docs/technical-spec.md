# Technical Specification

## Tech Stack

| Layer | Technology | Version (approx) |
|---|---|---|
| Language | Kotlin | 2.0+ |
| UI Framework | Jetpack Compose | BOM 2024.12+ |
| Min SDK | API 26 (Android 8.0) | ~95% device coverage |
| Target SDK | API 35 (Android 15) | Latest stable |
| HTTP Client | Retrofit 2 + OkHttp | 2.11+ / 4.12+ |
| JSON Parsing | Kotlinx Serialization | 1.7+ |
| Image Loading | Coil 3 (Compose) | 3.0+ |
| Local Storage | DataStore (Preferences) | 1.1+ |
| Credential Storage | EncryptedSharedPreferences (Tink) | 1.1+ |
| Dependency Injection | Hilt | 2.52+ |
| Video (future) | Media3 ExoPlayer | Reserved, not in v1 |
| Build System | Gradle Kotlin DSL | 8.9+ |

## Architecture Layers

```
immich-android/
├── app/
│   ├── src/main/java/com/dav3/immichframe/
│   │   ├── data/
│   │   │   ├── remote/          # Retrofit API interfaces, DTOs
│   │   │   ├── local/           # DataStore, EncryptedPrefs
│   │   │   └── repository/      # Repository implementations
│   │   ├── domain/
│   │   │   ├── model/           # Domain models (Album, Asset, Settings)
│   │   │   └── repository/      # Repository interfaces
│   │   ├── di/                  # Hilt modules
│   │   ├── ui/
│   │   │   ├── setup/           # Setup screen (URL + API key)
│   │   │   ├── albums/          # Album picker
│   │   │   ├── slideshow/       # Slideshow player
│   │   │   ├── settings/        # Settings screen
│   │   │   ├── components/      # Reusable composables
│   │   │   └── theme/           # Material 3 theme
│   │   ├── nav/                 # Navigation graph
│   │   └── ImmichFrameApp.kt    # Application class
│   ├── src/main/res/
│   └── build.gradle.kts
├── docs/                        # This documentation
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

## Security

- API key stored via `EncryptedSharedPreferences` (AES-256, backed by Android Keystore)
- API key never logged, never sent to any endpoint except the user's Immich server
- Server URL stored in DataStore (not sensitive)
- No telemetry, no analytics, no third-party tracking

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

| Data | Storage | Key |
|---|---|---|
| Server URL | DataStore | `server_url` |
| API Key | EncryptedSharedPreferences | `api_key` |
| Selected Album IDs | DataStore | `selected_album_ids` |
| Slideshow interval | DataStore | `slideshow_interval_sec` |
| Transition duration | DataStore | `transition_duration_sec` |
| Image fill mode | DataStore | `image_fill_mode` |
| Ken Burns | DataStore | `ken_burns_enabled` |
| Show clock | DataStore | `show_clock` |
| Keep screen on | DataStore | `keep_screen_on` |
