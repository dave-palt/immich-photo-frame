# ImmichFrame Key Manager

Standalone CLI tool to create or verify a scoped Immich API key for
ImmichPhotoFrame. No runtime dependencies — each binary is a single
self-contained executable.

## Download (recommended)

Grab the binary for your OS from the [GitHub Releases](https://github.com/dave-palt/immich-photo-frame/releases)
page. Look for files named `keymgr-<os>-<arch>`.

| File | OS | Architecture |
|---|---|---|
| `keymgr-darwin-arm64` | macOS | Apple Silicon (M1/M2/M3/M4) |
| `keymgr-darwin-x64` | macOS | Intel |
| `keymgr-linux-arm64` | Linux | ARM64 (e.g. Raspberry Pi 4/5) |
| `keymgr-linux-x64` | Linux | x86_64 |
| `keymgr-windows-x64.exe` | Windows | x86_64 |

### Quick start

```bash
# macOS/Linux — make executable after download
chmod +x keymgr-darwin-arm64
./keymgr-darwin-arm64 help
```

```powershell
# Windows
.\keymgr-windows-x64.exe help
```

## Usage

### Generate a scoped key

```bash
./keymgr generate <server-url> <email> [password]
```

```bash
# Example (you'll be prompted for password if omitted)
./keymgr generate https://photos.example.com:2283 user@example.com
```

Creates a least-privilege API key with only the 4 permissions ImmichPhotoFrame
needs: `album.read`, `asset.read`, `asset.view`, `user.read`. If an existing
`ImmichPhotoFrame` key is found, you'll be asked to update or recreate it.

### Check an existing key

```bash
./keymgr check <server-url> <api-key>
```

```bash
./keymgr check https://photos.example.com:2283 myApiKey123
```

Tests the 5 endpoints ImmichPhotoFrame uses and reports which permissions are
missing.

## Build from source

Requires [Bun](https://bun.sh) 1.2+.

```bash
# Build all 5 targets
./build.sh

# Build only for the current platform
./build.sh host
```

Binaries are output to `tools/keymgr/dist/`.

## Alternatives

Prefer shell scripts? The repo also includes POSIX shell versions in `scripts/`
(macOS/Linux) and PowerShell versions (Windows):

- `scripts/generate-api-key.sh` / `scripts/check-api-key.sh`
- `scripts/generate-api-key.ps1` / `scripts/check-api-key.ps1`
