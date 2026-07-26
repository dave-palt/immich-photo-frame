# CI/CD Setup

## Branching Strategy

- `develop` — active development branch. Push/PR triggers a debug APK build.
- `main` — production branch. Merges trigger a signed release AAB + APK build.
## CI/CD Setup

### Branching Strategy

- `develop` — active development branch. Push/PR triggers a debug APK build.
- `main` — production branch. Merges trigger a signed release AAB + APK build.
- Feature branches off `develop`: `feat/<description>`, `fix/<description>`.

### Dev Build (develop branch)

Triggers on push/PR to `develop` (workflow: `.github/workflows/dev-build.yml`).

Two parallel jobs:

#### Lint job
- Spotless code style check (`spotlessCheck`)
- Android Lint (`lintDebug`)
- Lint reports uploaded as artifacts (7-day retention)

#### Build job
- Decodes shared debug keystore from `DEBUG_KEYSTORE` secret
- Builds debug APK with `.debug` application ID suffix and `-dev` version name suffix
- APK signed with shared debug keystore (all dev builds share the same signature for clean upgrades over each other)
- Artifact: `immichframe-debug` (14-day retention)
- **On push to develop** (not PR): publishes a GitHub pre-release:
  - Tag: `dev-{full sha}` (explicitly pinned via `--target ${{ github.sha }}`)
  - Title: "Dev Build (unstable)"
  - Prerelease flag set
- **Auto-cleanup**: keeps only the 3 most recent dev releases, deletes older ones with `gh release delete --cleanup-tag`

#### Required GitHub Secrets (dev)

| Secret | Description |
|---|---|
| `DEBUG_KEYSTORE` | Shared debug keystore file, base64-encoded |
| `DEBUG_KEYSTORE_PASSWORD` | Debug keystore password |
| `DEBUG_KEY_ALIAS` | Debug key alias |
| `DEBUG_KEY_PASSWORD` | Debug key password |

The release is created via `gh release create` (not `softprops/action-gh-release`) for full control over tag dates and target commit. The `--target ${{ github.sha }}` flag is critical — without it, GitHub Actions' detached HEAD checkout causes the tag to be created on the wrong commit.

### Production Build (main branch)

Triggers on push to `main` or manual `workflow_dispatch` (workflow: `.github/workflows/prod-build.yml`).

- Sets up Bun to compile the `keymgr` cross-platform binary tools
- Cross-compiles 5 keymgr binaries via `scripts/build.sh`:
  `keymgr-darwin-arm64`, `keymgr-darwin-x64`, `keymgr-linux-arm64`,
  `keymgr-linux-x64`, `keymgr-windows-x64.exe`
- Decodes signing keystore from `SIGNING_KEYSTORE_BASE64` secret
- Builds signed release **AAB** (`bundleRelease`) with R8 minification + resource shrinking
- Builds signed release **APK** (`assembleRelease`)
- Uploads both as artifacts (90-day retention)
- Creates a GitHub Release with `softprops/action-gh-release@v3`
- Release assets include: APK, AAB, keymgr binaries for all platforms, and all key management scripts

#### Required GitHub Secrets (prod)

| Secret | Description |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The `.jks` keystore file, base64-encoded |
| `SIGNING_STORE_PASSWORD` | Keystore file password |
| `SIGNING_KEY_ALIAS` | Key alias name within the keystore |
| `SIGNING_KEY_PASSWORD` | Password for the specific key |

#### Generating the Keystore Locally

```bash
keytool -genkeypair \
  -alias release \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -keystore release.jks \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -dname "CN=ImmichFrame, OU=Mobile, O=dav3, L=City, ST=State, C=US"
```

#### Uploading to GitHub

```bash
# Base64-encode the keystore
base64 -i release.jks -o keystore.b64

# Add as repository secret:
# Settings → Secrets and variables → Actions → Secrets → New secret
# Name: SIGNING_KEYSTORE_BASE64
# Value: (paste contents of keystore.b64)

# Then add the remaining secrets:
# SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD
```

#### Local Release Build

For local signed builds, set the environment variables before running Gradle:

```bash
export SIGNING_STORE_FILE=/path/to/release.jks
export SIGNING_STORE_PASSWORD=<password>
export SIGNING_KEY_ALIAS=release
export SIGNING_KEY_PASSWORD=<password>

./gradlew bundleRelease
```

### API Key Manager Tooling (keymgr)

The production build compiles and releases the `keymgr` cross-platform CLI tool alongside the app artifacts. This tool helps users generate and validate Immich API keys with the exact permissions ImmichFrame requires.

#### Release Assets (prod build)

| Asset | Platform | Purpose |
|---|---|---|
| `immichframe-release.apk` | Android | Installable APK |
| `immichframe-release.aab` | Android/Play Store | App Bundle |
| `keymgr` | macOS, Linux | Compiled standalone Bun binary |
| `generate-api-key.sh` / `check-api-key.sh` | macOS, Linux | Bash scripts (curl) |
| `generate-api-key.ps1` / `check-api-key.ps1` | Windows (PowerShell 5.1+) | Native PowerShell scripts (Invoke-RestMethod) |

> **Windows users:** A pre-compiled `keymgr.exe` is not provided (Bun cannot cross-compile to Windows from macOS/Linux CI). Windows users should use the PowerShell scripts, or compile locally with `bun build scripts/keymgr.ts --compile --outfile keymgr.exe`.

All scripts are built from the same source of truth (`scripts/keymgr.ts`) to ensure consistent behavior across platforms.

### Local Debug Build

```bash
./gradlew clean spotlessApply spotlessCheck lintDebug assembleDebug
```

If no `DEBUG_KEYSTORE_PATH` env var is set, the build falls back to the default debug keystore at `~/.android/debug.keystore` (storepass: `android`, alias: `androiddebugkey`).

### Self-Update (GitHub Releases)

The app's self-update feature consumes GitHub releases. The behavior depends on
build type:

- **Release builds** (primary target): fetch `/releases/latest` and compare the
  `vX.Y.Z` tag against the installed `versionName` via semantic version comparison.
  If newer, download the APK and invoke the system installer.
- **Debug builds** (dev channel): list recent releases, pick the newest `dev-{sha}`
  tag, and compare its SHA against `BuildConfig.GIT_SHA`. If different, download
  and install.

Dev builds published to the `develop` branch feed the dev channel. Production
releases published to the `main` branch feed the release channel. Self-update is
disabled entirely for Play Store installs (see [functional-spec.md](functional-spec.md#F5d)).

### Play Store Publishing (Future)

The AAB produced by the production workflow is ready for Play Store upload. Future enhancement: add `r0adkll/upload-google-play@v1` action to automate Play Store publishing from the `main` branch workflow.

Requirements for Play Store:
1. Google Play service account JSON key (stored as `PLAY_SERVICE_ACCOUNT_JSON` secret)
2. Existing Play Console app listing
3. First upload must be manual (Play Store requirement for new apps)
The AAB produced by the production workflow is ready for Play Store upload.
Future enhancement: add `r0adkll/upload-google-play@v1` action to automate
Play Store publishing from the `main` branch workflow.

Requirements for Play Store:
1. Google Play service account JSON key (stored as `PLAY_SERVICE_ACCOUNT_JSON` secret)
2. Existing Play Console app listing
3. First upload must be manual (Play Store requirement for new apps)
