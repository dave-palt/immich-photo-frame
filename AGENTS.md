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
  (2) API key needs 4 scoped permissions (was 2): `album.read`, `asset.read`,
  `asset.view`, `user.read`; (3) Immich v3 uses `key` query param, not
  `apiKey` — documented as a known caveat in api-reference.md and technical-spec.md.

<!-- Append new clarifications below this line. -->

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
