# CI/CD Setup

## Branching Strategy

- `develop` — active development branch. Push/PR triggers a debug APK build.
- `main` — production branch. Merges trigger a signed release AAB build.
- Feature branches off `develop`: `feat/<description>`, `fix/<description>`.

## Dev Build (develop branch)

Triggers on push/PR to `develop`. Builds a debug APK with `.debug` application ID suffix
and `-dev` version name suffix. No signing required.

Artifact: `immichframe-debug.apk` (14-day retention).

## Production Build (main branch)

Triggers on push to `main`. Builds a signed release AAB with R8 minification and
resource shrinking.

### Required GitHub Secrets / Variables

The production workflow uses **repository variables** (non-secret) and **repository secrets**
(sensitive). Configure these in GitHub Settings → Secrets and variables → Actions.

#### Variables (Settings → Secrets and variables → Actions → Variables tab)

| Variable | Description |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The `.jks` keystore file, base64-encoded |

#### Secrets (Settings → Secrets and variables → Actions → Secrets tab)

| Secret | Description |
|---|---|
| `SIGNING_STORE_PASSWORD` | Keystore file password |
| `SIGNING_KEY_ALIAS` | Key alias name within the keystore |
| `SIGNING_KEY_PASSWORD` | Password for the specific key |

### Generating the Keystore Locally

```bash
keytool -genkeypair \
  -alias immichframe \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -keystore immichframe.jks \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -dname "CN=ImmichFrame, OU=Mobile, O=dav3, L=City, ST=State, C=US"
```

### Uploading to GitHub

```bash
# Base64-encode the keystore for storage as a variable
base64 -i immichframe.jks -o keystore.b64

# Copy the content and add it as a repository variable:
# Settings → Secrets and variables → Actions → Variables → New variable
# Name: SIGNING_KEYSTORE_BASE64
# Value: (paste contents of keystore.b64)

# Then add the secrets:
# SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD
```

### Local Release Build

For local signed builds, set the environment variables before running Gradle:

```bash
export SIGNING_STORE_FILE=/path/to/immichframe.jks
export SIGNING_STORE_PASSWORD=<password>
export SIGNING_KEY_ALIAS=immichframe
export SIGNING_KEY_PASSWORD=<password>

./gradlew bundleRelease
```

## Play Store Publishing (Future)

The AAB produced by the production workflow is ready for Play Store upload.
Future enhancement: add `r0adkll/upload-google-play@v1` action to automate
Play Store publishing from the `main` branch workflow.

Requirements for Play Store:
1. Google Play service account JSON key (stored as `PLAY_SERVICE_ACCOUNT_JSON` secret)
2. Existing Play Console app listing
3. First upload must be manual (Play Store requirement for new apps)
