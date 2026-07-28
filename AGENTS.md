# AGENTS.md

Instructions for AI coding agents working on this repository.
Read this before making any changes.

## Project

ImmichFrame — native Android photo frame app for Immich.
Package: `com.dav3.immichframe`. Kotlin + Jetpack Compose + Hilt.
Local directory: `~/Documents/git/immich-android/` (intentionally not renamed).
GitHub: `dave-palt/immich-photo-frame`.

## Build

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home \
  ./gradlew clean spotlessApply spotlessCheck lintDebug assembleDebug --no-daemon --no-configuration-cache
```

JDK 17 is required. Always run `spotlessApply` before `spotlessCheck` — the
formatter and the check must agree.

Suppressed ktlint rules (in `app/build.gradle.kts`):
- `no-wildcard-imports` (Compose convention)
- `function-naming` (Composable functions are PascalCase)

## Git Workflow

1. `git fetch --all` before starting.
2. Branch off `origin/develop`: `feat/<description>` or `fix/<description>`.
3. Commit as `dave-palt` (`git@dav3.cc`).
4. PRs target `develop`. `main` is production-only (merged via PR).
5. NEVER force-push to `develop` or `main`.

## Architecture (one-liner)

```
UI (Compose) → ViewModel (StateFlow) → Repository → Retrofit (x-api-key header)
                                      ↕
                                   DataStore / EncryptedSharedPreferences
```

- Single shared DataStore (`DataStoreProvider.kt`). Never create a second
  `preferencesDataStore` delegate — it crashes with `IllegalStateException`.
- Image/video URLs append `?apiKey=<key>` for Coil/ExoPlayer (no custom HTTP
  client). Retrofit calls use the `x-api-key` header via OkHttp interceptor.
- Self-update uses `BuildConfig.GIT_SHA` vs GitHub release tag (`dev-{sha}`).

## Docs Must Stay in Sync (MANDATORY)

**Every code change that adds, removes, or modifies a user-facing feature MUST
be accompanied by a corresponding docs update in the same commit/PR.** No
exceptions. "I'll doc it later" is how docs rot.

The docs live in `docs/`:

| Doc | What it covers | When to update |
|---|---|---|
| `docs/overview.md` | Goals, non-goals, high-level architecture | Add/remove a goal or non-goal |
| `docs/functional-spec.md` | User flows (F1–F6), settings list, error handling | Add/change a user flow, setting, or feature behavior |
| `docs/technical-spec.md` | Tech stack, package layout, state persistence keys, permissions | Add a dependency, new package, DataStore key, or permission |
| `docs/ui-spec.md` | Screen layouts, settings UI mockup | Add/change a UI element or settings toggle |
| `docs/api-reference.md` | Immich + GitHub API endpoints used | Change an endpoint, auth method, or API key scope |
| `docs/ci-cd.md` | Branching, workflows, secrets, signing | Change CI workflow, secrets, or release process |
| `README.md` | Public-facing summary, feature list | Feature list changes, new requirement |

### Update checklist (run through after EVERY feature change)

1. **New setting added?** → Update ALL of:
   - `SlideshowSettings` in `Models.kt` (source of truth for the model)
   - `SettingsRepositoryImpl.kt` (DataStore key + read + write)
   - `SettingsViewModel.kt` (toggle/update function)
   - `SettingsScreen.kt` (UI toggle)
   - `docs/functional-spec.md` (F6 settings list)
   - `docs/technical-spec.md` (State Persistence table)
   - `docs/ui-spec.md` (settings screen mockup)
   - `README.md` (feature list, if user-facing)

2. **New API endpoint used?** → Update:
   - `ImmichApi.kt` (Retrofit interface)
   - `Dtos.kt` (request/response DTOs)
   - `docs/api-reference.md` (endpoint documentation)
   - `docs/technical-spec.md` (if new auth method or permission)

3. **New dependency added?** → Update:
   - `gradle/libs.versions.toml` (version catalog)
   - `app/build.gradle.kts` (dependency reference)
   - `docs/technical-spec.md` (Tech Stack table)

4. **New permission added?** → Update:
   - `AndroidManifest.xml`
   - `docs/technical-spec.md` (Permissions table)

5. **CI/CD changed?** → Update:
   - The relevant workflow in `.github/workflows/`
   - `docs/ci-cd.md`

6. **Non-goal becomes a goal (or vice versa)?** → Update:
   - `docs/overview.md` (Goals / Non-Goals)

7. **New feature worth touring?** → Update:
   - `TourSteps` in `ui/onboarding/TourStep.kt` (add step to the relevant
     screen's list; pick a stable `id`, `targetKey`, and string resources)
   - `Modifier.tourTarget("<targetKey>", tourState)` on the UI element the
     step highlights (omit `targetKey` for centered, no-spotlight tips)
   - `strings.xml` — add `tour_<screen>_<step>_title` + `_body` (EN + 12 locales)
   - `docs/functional-spec.md` — update F7 step inventory + step count
   - `docs/technical-spec.md` — update the `onboarding_completed_steps` count
     if the total number of steps changed

### How to update docs

- Read the current doc first (`read_file`), then `patch` or `write_file` the
  changed sections. Don't rewrite entire docs for a one-line addition.
- Keep descriptions factual and matching the code. If unsure what the code
  does, read the relevant `.kt` file before writing prose about it.
- Settings tables in `technical-spec.md` must list the exact DataStore key
  name and type. Cross-check against `SettingsRepositoryImpl.kt` `Keys` object.
- UI mockups in `ui-spec.md` should reflect the actual section ordering in
  `SettingsScreen.kt`.

## User Clarifications Log

When the user gives a clarification, correction, or design decision, record
it here (newest first) AND apply it to the relevant doc. This section is the
authoritative override — if something below conflicts with a doc, the entry
below wins until the doc is fixed.

<!--
Format:
- **[DATE]** — Topic. What was clarified. Which doc(s) updated.
-->

- **2026-07-25** — Full docs alignment pass. All 6 docs + README rewritten to
  match implementation state as of commit d06759e. Added AGENTS.md (this file)
  with the sync rules. Key corrections: (1) API endpoint for album assets is
  `POST /search/metadata`, not `GET /albums/{id}` (Immich v3 compatibility);
  (2) API key needs 5 scoped permissions: `album.read`, `asset.read`,
  `asset.view`, `asset.download` (for video playback), `user.read`;
  (3) Immich v3 uses `key` query param, not
  `apiKey` — documented as a known caveat in api-reference.md and technical-spec.md.

<!-- Append new clarifications below this line. -->

- **2026-07-29** — Fixed Settings → Back navigation bug + removed redundant
  slideshow close button + added Settings to onboarding flow. (1) Root cause
  of back-nav bug: `apiKey` in `SettingsRepositoryImpl` was a cold one-shot
  `flow { emit(...) }` that emitted once and completed — it never reacted to
  `EncryptedSharedPreferences` writes. `NavViewModel.startRoute` (which
  `combine`s `serverUrl`, `apiKey`, `selectedAlbumIds`) therefore permanently
  evaluated to `SETUP` after the initial collection (where `apiKey=""`), even
  after the user entered a key. Settings `onBack` reads `startRoute` → got
  `SETUP` → sent user to domain page. Fix: `apiKey` is now a
  `MutableStateFlow` backed by `EncryptedSharedPreferences`, updated in
  `setApiKey()` and `clearAll()`. No navigation restructure needed — the
  existing state-driven design (where `startRoute` computes the destination
  and `onBack` uses `popUpTo(0)`) is correct once the flow is live. The same
  `startRoute` mechanism provides "two hooks" for Settings back: during
  onboarding (key set, no albums) → Albums; at runtime (albums selected) →
  Slideshow. (2) Removed the X (close) button from the slideshow top bar —
  it was redundant with the albums (PhotoLibrary) icon, both navigating back
  to album selection. Removed `onClose` param from `SlideshowScreen`, the
  `Icons.Default.Close` import, the `slideshow_close` tour step
  (TourStep.kt), and `tour_slideshow_close_title` + `_body` strings from all
  13 locale files. Tour step count: 21→20 (slideshow 8→7). (3) Inserted
  Settings into the first-run onboarding flow: Setup → Settings → Albums →
  Slideshow (was Setup → Albums → Slideshow). Single `SetupScreen.onSuccess`
  change in ImmichNavHost: navigate to SETTINGS instead of ALBUMS. Settings
  back is driven by `startRoute`, so it automatically goes to Albums
  (first-run, no albums yet). Updated: functional-spec (F1 step 7, F7 step
  inventory + count + slideshow behavior description).

- **2026-07-28** — Biometric-gated album selection from slideshow. The
  PhotoLibrary (albums) icon in the slideshow top bar now requires
  biometric/device-credential auth before navigating to album selection,
  matching the media-selection grid icon next to it. Both modify what's
  shown on the frame and sit side-by-side in the top bar, so both should
  require auth. Reuses the existing `biometric` launcher +
  `showBioNotSetup` dialog already declared in the top-bar Row for media
  selection. New string `biometric_auth_subtitle_albums` in all 13 locales.
  Scope: slideshow top bar only — Settings → Albums and onboarding album
  selection are NOT gated (those are already behind the Settings screen /
  first-run flow). Updated: functional-spec (F3 album icon biometric-gated,
  F7 tour step description).

- **2026-07-28** — Animated GIF playback support. Two compounding gaps
  prevented GIFs from playing: (1) no `coil-gif` dependency and no
  `GifDecoder` registered with Coil's `ImageLoader`, so GIFs decoded as a
  single static frame; (2) image URLs pointed at `/thumbnail?size=preview`,
  which Immich transcodes to JPEG — collapsing GIF animation regardless of
  decoder. Fix: added `io.coil-kt.coil3:coil-gif` dependency (same version
  as coil, pinned in version catalog). `ImmichFrameApp` now implements
  `SingletonImageLoader.Factory` and registers `GifDecoder.Factory()` +
  crossfade in `newImageLoader()`. The `AssetDto` gained
  `originalMimeType` (returned by `POST /search/metadata`), carried through
  `Asset` model → `CachedAsset` → Room (`original_mime_type` column, DB
  version bumped 1→2 with `fallbackToDestructiveMigration`).
  `ImmichRepositoryImpl.imageUrl()` now branches: GIFs route to
  `/original?apiKey=`, all other images stay on
  `/thumbnail?size=preview&apiKey=`. `SlideshowViewModel.imageUrl()`
  changed signature from `(assetId: String)` to `(asset: Asset)` so the
  mime type is available at the call site. The media-selection grid
  thumbnails are unaffected (they use `size=thumbnail`, always JPEG —
  fine for GIFs as a static preview). Updated: functional-spec (F3 step
  5 — GIF playback), technical-spec (tech stack, image caching strategy,
  media cache schema, persistence note), api-reference (new Media URL
  Routing section), README (feature list + tech stack).

- **2026-07-27** — Night Mode feature (brightness-based display schedule).
  Unlike a true screen-off or device power-off, this dims the screen to a
  configurable brightness level during set hours via per-window
  `WindowManager.LayoutParams.screenBrightness`. The screen stays on — this
  is explicitly a fallback for devices that lack built-in scheduled power
  on/off. The Settings UI includes a helper text under the toggle stating
  that the device's native scheduled power on/off (if available) is
  preferable. New settings: `night_mode` (bool, default false),
  `night_mode_start` (int minutes, default 1320 = 22:00), `night_mode_end`
  (int minutes, default 420 = 07:00), `night_mode_brightness` (int 0–100,
  default 0). `SlideshowSettings.isNightModeActive(hourMinute)` handles
  overnight wrap-around. `SlideshowScreen` has a `LaunchedEffect` polling
  every 60s + a `DisposableEffect` applying/restoring brightness. New
  Settings UI section "Night Mode" with toggle, two `TimePicker` dialogs
  (24h), brightness slider, and the last-resort helper text. New strings
  (EN + 12 locales): section_night_mode, night_mode, night_mode_desc,
  night_mode_alt_hint, night_mode_start, night_mode_end, night_mode_brightness,
  night_mode_brightness_hint. Updated: functional-spec (F6 settings list),
  technical-spec (persistence table + data flow), ui-spec (mockup + section
  list), overview (goals), README (feature list).

- **2026-07-27** — Per-endpoint permission verification. After key generation
  or manual paste, the app probes all 5 required endpoints (mirroring
  `scripts/check-api-key.sh`) in dependency order: `GET /users/me` →
  `GET /albums` → `POST /search/metadata` → `GET /assets/{id}/thumbnail` →
  `GET /assets/{id}/original`. Results stored as `permission_status` (JSON)
  in DataStore, refreshed on Settings open + "Re-check" button. Blocking
  permissions (user/album/asset read/view) missing → setup blocked with
  error. Optional permission (`asset.download`) missing → degraded mode:
  Skip Videos toggle locked ON, media cache skips downloading. New
  `RequiredPermission` enum in `domain/model/` is the single source of truth
  (scope string, feature name, blocking flag, gated setting key). New model
  types: `PermissionStatus` (sealed: Granted/Denied/Unknown),
  `PermissionCheckResult` (map + computed `canProceed`/`missingBlocking`/
  `missingOptional`). New DataStore key: `permission_status`. New repo
  method: `ImmichRepository.checkPermissions()` returning Result. New
  Settings UI: `PermissionStatusCard` composable with ✓/✗/? icons per
  permission + error-colored background when blocking missing. `SwitchItem`
  gained `enabled` param for locked toggles. Updated: functional-spec (F1
  permission verification step, F6 permission card + feature gating),
  technical-spec (persistence table), ui-spec (permission card mockup +
  description), api-reference (permission verification section), README
  (feature list).

- **2026-07-27** — In-app API key generation feature. Eliminated the need for
  external `keymgr.ts` scripts. Setup screen restructured into a two-step flow:
  (1) Domain Validation — user enters server URL, app calls `GET /server/version`
  + `GET /server/features` (no auth) to detect version and auth methods; (2)
  Authentication — three options side-by-side: Generate Key (email/password →
  `POST /auth/login` → `POST /api-keys`), Enter Manually (paste existing key),
  OAuth (PKCE via Custom Tabs, only shown if server has OAuth enabled). Password
  is held in ViewModel memory only for the key-generation call, then discarded —
  never persisted. Only the resulting API key hits EncryptedSharedPreferences.
  New separate Retrofit instance `ImmichAuthApi` (no x-api-key interceptor) for
  login, key creation, and server probing; Bearer token passed per-call. New
  `PkceHelper.kt` for OAuth code_verifier/challenge/state. New DataStore keys:
  `server_version` (String), `api_key_scoped` (String bool). New dependency:
  `androidx.browser:browser:1.8.0` (Custom Tabs). New manifest deep-link
  intent-filter for `com.dav3.immichframe://oauth-callback`. New setup tour
  step `setup_validate` (total: 18→19... actually 20 with the count fix). New
  strings (EN + 12 locales): validate_server, server_version_label,
  generate_key, enter_manually, generate_key_title, generate_key_desc, email,
  password, login_and_generate, sign_in_with_oauth, api_key_help,
  api_key_help_title, api_key_help_what, api_key_help_why,
  api_key_help_password + tour_setup_validate_title/body (updated
  tour_setup_apikey + tour_setup_connect body text). Confirmed via git
  archaeology (v1.50→v3.0.3): Immich NEVER had a `/auth/tfa` endpoint — TFA
  is OAuth/OIDC-only (enforced by the IdP, not the server API). Updated:
  functional-spec (F1 rewrite, F7 step count), technical-spec (tech stack,
  package layout, persistence table, setup description), ui-spec (setup
  mockup redraw), api-reference (new auth endpoints section, in-app gen
  note), overview (goals), README (features, setup, permissions table fix
  4→5 including missing asset.download).

- **2026-07-26** — Media Selection feature (PR2 of media-selection feature).
  New screen `ui/media/MediaSelectionScreen.kt` + `MediaSelectionViewModel`
  showing a grid of all album assets. Tap thumbnails to show/hide from the
  slideshow. "Show new photos by default" switch (default ON): ON = all start
  shown, tap to hide; OFF = all start hidden, tap to show. Flipping preserves
  current visible selection (recomputes stored toggled set against all assets).
  Show All / Hide All bulk buttons. Counter "X of Y shown". Accessible from
  slideshow top bar (GridView icon next to photo count), biometric-gated.
  New DataStore keys: `media_selection_toggled_ids` (StringSet),
  `media_selection_new_shown` (String bool, default true).
  `SlideshowViewModel.load()` now applies media-selection filter via shared
  `applyMediaSelection()` helper. New nav route `media_selection`. New strings
  (EN + 12 locales): media_selection_title, media_selection_count,
  media_selection_new_items_default, media_selection_select_all,
  media_selection_select_none, media_selection_shown, media_selection_hidden.
  Updated: functional-spec (F4 controls, new F8 flow), technical-spec
  (persistence table, package layout, nav routes), README.
- **2026-07-27** — Offline cache display + album lifecycle fix. (1) Root
  cause: cached files were downloaded to disk by MediaCacheWorker but never
  used for display — `imageUrl`/`videoUrl` always returned network URLs, so
  the slideshow showed a black screen when the server was offline despite
  having a populated cache. Fix: `SlideshowViewModel` now resolves local
  `file://` URIs from `MediaCacheRepository.getAssetFilePaths()` (new batch
  lookup method) on `load()`, falling back to network on cache miss. Same
  pattern applied to `MediaSelectionViewModel.thumbnailUrl()`. (2) Album
  deletion detection: `MediaCacheWorker` now distinguishes 404 (album
  permanently gone) from transient errors. On 404, the album's cache is
  purged; if all selected albums are gone, `selected_album_ids` is cleared
  → `NavViewModel` routes back to album selection. `SlideshowViewModel`
  also detects 404 on cold-start fetch and sets `albumGone` flag →
  `SlideshowScreen` navigates to album selection via `LaunchedEffect`.
  (3) Empty-response guard: reconcile step only prunes cached assets when
  the remote list is non-empty (prevents wiping cache on transient
  search-service hiccups). (4) "No albums available" empty state on
  AlbumSelectionScreen (distinct from server-unreachable error state).
  New `SlideshowUiState.albumGone` field. New strings (EN + 12 locales):
  `no_albums_available`, `no_albums_available_desc`. Updated: functional-
  spec (F2 empty states, F3 offline/album lifecycle, Error Handling
  table), technical-spec (sync lifecycle, image caching strategy), ui-spec
  (album selection empty states), README.
- **2026-07-26** — Biometric auth + API key security (PR1 of media-selection
  feature). Added `androidx.biometric:biometric:1.1.0` dependency.
  `MainActivity` changed from `ComponentActivity` to `FragmentActivity`
  (required by `BiometricPrompt`; `FragmentActivity` extends
  `ComponentActivity` so all existing APIs still work). New:
  `domain/system/BiometricHelper.kt` (capability check, prompt with
  `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` — allows PIN fallback, cancellable
  with no penalty), `ui/components/BiometricLauncher.kt` (composable
  wrapper: `rememberBiometricLauncher()`). API key in Settings Connection
  section: Edit now empties the field (no pre-population); Reveal and Copy
  buttons appear when key is set, both biometric-gated; copy shows a
  snackbar. If no screen lock enrolled, a dialog links to security settings.
  New strings (EN + 12 locales): reveal, hide, copy, api_key_copied,
  biometric_auth_title, biometric_auth_subtitle_key,
  biometric_auth_subtitle_media, biometric_not_setup_title,
  biometric_not_setup_message. Updated: functional-spec (F6 Connection
  section), technical-spec (tech stack, package layout), ui-spec (mockup +
  description), README.
- **2026-07-26** — Launcher icon redesign + app logo. (1) Replaced the old
  5-segment colored frame icon with a new design: colorful frame border +
  sun/moon circle + mountain silhouette (converted from 512-space SVG to
  108dp Android VectorDrawable). (2) Foreground content wrapped in `<group
  scaleX/Y=0.75>` to add ~12% padding (opaque square was showing under
  launcher mask). (3) Removed legacy PNG fallbacks in mipmap-hdpi/mdpi/
  xhdpi/xxhdpi/xxxhdpi — unreachable since minSdk=26 uses adaptive icons.
  (4) Created dedicated `app_logo.xml` drawable (no background fill, full
  viewport, no scaling) for the Setup screen; updated from 72dp foreground
  to 120dp logo. (5) Debug variant keeps amber bg (#FFB400) with navy
  replacing the orange segment. Variants: day (drawable/), night
  (drawable-night/), monochrome (drawable/), debug (debug/drawable/).
  Updated: technical-spec (resource layout), ui-spec (setup logo),
  README (icon feature list).


- **2026-07-26** — Onboarding tour post-PR fixes. (1) Added two new slideshow
  tour steps: `slideshow_albums` (back-to-album-selection button) and
  `slideshow_update` (update status icon, force-shown as dimmed placeholder
  during tour since the icon normally only appears when an update is available).
  Total slideshow steps: 5→7, overall: 16→18. (2) Centered (no-target) tour
  steps were rendering at screen top — fixed by adding `contentAlignment =
  Alignment.Center` to the overlay Box. (3) Per-screen tour reset:
  `resetOnboardingForScreen(stepIds)` in SettingsRepository +
  `resetOnboardingForSettings()` in SettingsViewModel; Settings now has two
  buttons: "Show Tour Again" (Settings-only) + "Reset All Tours" (all screens);
  Setup screen also has "Show Tour Again". (4) Target lifecycle tracking
  rewritten — `tourTarget()` modifier uses `DisposableEffect` to register/
  unregister in `presentKeys` set; TourHost computes `readySteps` from
  pendingSteps whose targets are present, deferring activation until targets
  appear. (5) Black spotlight bug fixed: `CompositingStrategy.Offscreen` on
  Canvas so `BlendMode.Clear` punches a transparent hole. (6) Settings back-
  navigation stuck bug fixed: `onBack` uses explicit route via `startRoute` +
  `popUpTo(0)` instead of `popBackStack()`. (7) Slideshow controls now respect
  system bar insets (`statusBarsPadding` on top bar, `navigationBarsPadding`
  on bottom progress bar + play/pause row). (8) Setup screen: added app icon
  (launcher foreground drawable, 72dp), `imePadding()` so keyboard doesn't
  hide the connect button. Updated: functional-spec (F7 step inventory + 18
  count), technical-spec (resource layout), ui-spec (overlay centering, setup
  logo, tour buttons, insets), README (tour step detail + icon variants).

- **2026-07-26** — Onboarding Tour feature. Added a modular, per-step
  coachmark tour system that auto-triggers on screen entry for any
  un-completed steps. Per-step completion tracking via
  `onboarding_completed_steps` StringSet in DataStore (not a single boolean).
  16 steps across 4 screens: Setup (4), Albums (3), Slideshow (5), Settings
  (4). Custom Compose overlay — no third-party showcase library. New package
  `ui/onboarding/` with `TourStep.kt` (step registry), `TourState.kt` (state
  holder + `tourTarget` modifier), and `CoachmarkOverlay.kt` (scrim +
  spotlight + tooltip card + `TourHost` wrapper). Settings → System section
  gains a "Show Tour Again" button (`resetOnboarding()`). Slideshow forces
  controls visible + suppresses auto-hide during tour. Settings scrolls target
  sections into view. 37 new strings (EN + 12 locales). Updated:
  functional-spec (F7), technical-spec (persistence table + package layout),
  ui-spec (overlay description), README.
- **2026-07-26** — Launcher Mode feature. After real-hardware testing on a
  Realme PKH110 (ColorOS 16 / Android 16), BOOT_COMPLETED was confirmed to
  never fire (boot_verified stayed false across 2 reboots) — this is both
  the known Android 15/16 platform bug (issuetracker #471573182) and the
  Chinese OEM autostart restriction. Fix: added **Launcher Mode** as a
  third autostart option. The app declares an `activity-alias` (`.LauncherAlias`)
  in the manifest with `HOME`+`DEFAULT` categories, `enabled="false"`. Toggling
  Launcher Mode on calls `PackageManager.setComponentEnabledSetting()` to
  enable the alias, making the app appear as a Home launcher — the system
  always launches the default Home on boot, no BOOT_COMPLETED needed. This is
  the most reliable method, especially for dedicated photo frames. A new
  `LauncherHelper.kt` (`domain/system/`) provides `setLauncherModeEnabled()`,
  `isLauncherModeEnabled()`, `isDefaultLauncher()`, and
  `openOtherLauncher()` (opens `ACTION_HOME_SETTINGS` so the user can switch
  launchers to access other apps). New setting: `launcher_mode` (bool, default
  false). New strings: `launcher_mode`, `launcher_mode_desc`,
  `open_other_launcher` (EN + 12 locales). Updated: functional-spec (F5c
  renamed from Self-Update to Launcher Mode; Self-Update is now F5d; F6
  settings list), technical-spec (persistence table, package layout,
  MainActivity description), ui-spec (mockup + description), README.
  Note: the SAW permission and BootReceiver code from the earlier change are
  retained — Start on Boot and Launcher Mode are complementary; the user can
  use either or both.
- **2026-07-26** — Start on Boot fix. Root cause: on Android 10+ (API 29+),
  calling `startActivity()` from a `BOOT_COMPLETED` `BroadcastReceiver` is
  blocked by the Background Activity Launch (BAL) restriction — the OS silently
  refuses to bring a background app to the foreground. Fix: added
  `SYSTEM_ALERT_WINDOW` permission ("Display over other apps"), which is a
  documented BAL exemption. BootReceiver now guards the `startActivity` call
  with `Settings.canDrawOverlays()`. The manifest receiver also changed to
  `exported="true"` (dropped the misleading `permission` attribute). Settings
  UI adds a "Grant Display Over Other Apps" button (with dialog + lifecycle-
  aware re-check via `DisposableEffect`/`ON_RESUME`) shown when Start on Boot
  is on but SAW is missing. Added 3 strings (`overlay_perm_title`,
  `overlay_perm_message`, `open_overlay`) in EN + 12 locales. Updated:
  functional-spec (F5b, F6), technical-spec (permissions table, package
  layout), ui-spec, README.
- **2026-07-25** — Added Media Cache feature (Room + WorkManager). New
  packages: `data/local/` (Room DB, DAOs, entities, cache repo impl,
  converters) and `data/sync/` (MediaCacheWorker, SyncScheduler). New
  settings: `autoSync` (bool, default true) + `syncIntervalMinutes` (int,
  default 30, clamped to 15 min by WorkManager). SlideshowViewModel now
  loads cache-first, falls back to network on cold start, and delegates
  background sync to SyncScheduler.syncNow() (which enqueues
  MediaCacheWorker). New deps: Room 2.7.1, WorkManager 2.9.1, Hilt-Work
  1.2.0. AndroidManifest removes default WorkManagerInitializer.
  ImmichFrameApp implements Configuration.Provider for HiltWorkerFactory.
  Updated: functional-spec (F3, F6), technical-spec (tech stack, package
  layout, state persistence, media cache section), ui-spec (settings
  mockup), overview (goals), README.

- **2026-07-25** — Burn-in Protection setting removed. Photo animations now
  serve double duty as burn-in protection (the slow pan/zoom is what prevents
  OLED burn-in for always-on displays). Removed: `burnInProtection` field from
  `SlideshowSettings`, `BURN_IN` DataStore key, `toggleBurnInProtection()`,
  `BurnInProtectionSetting` composable, and the 3 `burn_in_*` strings (EN + 12
  locales). The clock drift feature is retained but now gated on
  `photoAnimations` (renamed param `burnInProtection` → `driftProtection` in
  `DraggableClock`). Settings count: 25 → 24. Updated: functional-spec,
  technical-spec, ui-spec, overview, README.

- **2026-07-25** — Added boot verification self-test. `BootReceiver` now writes
  a `bootVerified` flag to DataStore on every successful `BOOT_COMPLETED`
  reception. Toggling `Start on Boot` resets it to false. The "Open Autostart
  Settings" button in Settings now only shows when the device is a restricted
  OEM, startOnBoot is on, and bootVerified is false — i.e., the receiver hasn't
  fired yet since the toggle. This fixes the false-positive UX where the button
  always showed even after the feature was working. Settings count: 24 → 25
  (bootVerified is internal, not user-facing as a separate setting). Added
  `boot_not_verified_desc` string (EN + 12 locales).

## Things to Never Do

- Create a second `preferencesDataStore` delegate (crashes at runtime).
- Hardcode the API key, server URL, or any user secret in code.
- Use `git push --force` on `develop` or `main`.
- Commit the release keystore (`release.jks`) or debug keystore.
- Add a setting to `Models.kt` without adding it to the repo, ViewModel, UI,
  AND docs in the same change.
- Skip `spotlessApply` before committing (CI will fail on `spotlessCheck`).
