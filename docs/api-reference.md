# Immich API Reference

Base URL: user-provided (e.g. `https://photos.example.com:2283` or `http://192.168.1.100:2283`)

All endpoints are under `/api` prefix.

## Authentication

Send the API key as an HTTP header:

```
x-api-key: ***
```

The app uses this header for all Retrofit API calls (injected via an OkHttp
interceptor in `ImmichRepositoryImpl.kt`).

For image/video URLs fetched by Coil and ExoPlayer (which do not use the
Retrofit client), the API key is appended as a query parameter:

```
?apiKey=<key>
```

> **Immich v3 note**: Immich v3 renamed the query parameter from `apiKey` to
> `key`. The app currently uses `apiKey` for image/video URLs. If your Immich
> server is on v3 and images fail to load (404/401), this is the likely cause.
> The `x-api-key` header used by Retrofit calls is unaffected and works on
> both v1 and v3.

API keys can be created in Immich under User Settings > API Keys, and can be
scoped to specific permissions.

### Required Permissions

The API key needs 5 scoped permissions:

| Permission | Used for |
|---|---|
| `album.read` | List albums, get album info |
| `asset.read` | Search/list assets in albums (POST /search/metadata) |
| `asset.view` | View thumbnails and previews (images via `?apiKey=`) |
| `asset.download` | Download original files (video playback via ExoPlayer) |
| `user.read` | Validate API key (GET /users/me) |

These permissions are the minimum required for ImmichFrame to function. You can create a key with these exact permissions using the provided `keymgr` tool (or the shell/PowerShell scripts). Requires Immich v1.135+ for scoped keys.

## API Key Management Tools

ImmichFrame provides several cross-platform tools for generating and validating API keys:

| Tool | Platform | Description |
|---|---|---|
| `keymgr` | macOS, Linux, Windows (compiled binary) | Unified CLI: `keymgr generate <url> <email>` and `keymgr check <url> <key>` |
| `keymgr.exe` | Windows | Pre-compiled Windows binary |
| `generate-api-key.sh` / `check-api-key.sh` | macOS, Linux | Bash scripts using `curl` |
| `generate-api-key.ps1` / `check-api-key.ps1` | Windows (PowerShell 5.1+) | Native PowerShell scripts using `Invoke-RestMethod` |

### Quick Start (macOS/Linux)

```bash
# Download the release assets from GitHub Releases
chmod +x keymgr
./keymgr generate https://photos.example.com user@example.com
# Enter password when prompted
# Copy the output key into ImmichFrame Settings > API Key
./keymgr check https://photos.example.com <your-api-key>
```

### Quick Start (Windows PowerShell)

```powershell
# Download the release assets from GitHub Releases
.\keymgr.exe generate https://photos.example.com user@example.com
# Enter password when prompted
.\keymgr.exe check https://photos.example.com <your-api-key>
```

Or use the native PowerShell scripts directly:
```powershell
.\generate-api-key.ps1 https://photos.example.com user@example.com
.\check-api-key.ps1 https://photos.example.com <your-api-key>
```

> **Note**: All tools require Immich v1.135+ for scoped API key support. The server version is checked automatically.
### 1. Health Check

```
GET /api/server/ping
```

No auth required. Returns:

```json
{ "resp": "pong" }
```

Used during setup to verify the server is reachable.

---

### 2. Validate API Key / Get Current User

```
GET /api/users/me
```

Auth: `x-api-key`

Returns user info including email, name, ID. Used during setup to verify
the API key is valid.

Response (abbreviated):

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "name": "User Name",
  ...
}
```

---

### 3. List Albums

```
GET /api/albums
```

Auth: `x-api-key`

Returns array of albums:

```json
[
  {
    "id": "album-uuid",
    "albumName": "Vacation 2024",
    "assetCount": 142,
    "albumThumbnailAssetId": "asset-uuid"
  }
]
```

The app extracts `id`, `albumName`, `assetCount`, and `albumThumbnailAssetId`.

---

### 4. Search Assets by Album (Metadata Search)

```
POST /api/search/metadata
```

Auth: `x-api-key`

> **Note**: The app previously used `GET /api/albums/{id}` to fetch album
> assets, but switched to the search endpoint for reliability with Immich v3.
> The album details endpoint returns assets inconsistently across versions.

Request body:

```json
{
  "albumIds": ["album-uuid"],
  "size": 1000
}
```

Response:

```json
{
  "albums": { "total": 1, "count": 1, "items": [...] },
  "assets": {
    "total": 142,
    "count": 142,
    "items": [
      { "id": "asset-uuid", "type": "IMAGE" }
    ]
  }
}
```

The app extracts `id` and `type` from each asset in `assets.items`. Assets
with `type == "VIDEO"` are handled by ExoPlayer (when Skip Videos is off).

DTOs are in `Dtos.kt` (`SearchMetadataRequest`, `SearchMetadataResponse`,
`SearchAssetsDto`, `AssetDto`).

---

### 5. Download / View Asset

```
GET /api/assets/{id}/thumbnail?size=preview
```

Auth: `x-api-key` header **or** `?apiKey=<key>` query param.

Returns a web-friendly preview image. The `size` parameter accepts:
- `thumbnail` — small (~100px), used for album picker thumbnails
- `preview` — medium/large (~1440p), used for slideshow display

The app requests `size=preview` for all slideshow images to optimize
bandwidth and disk cache usage.

For video playback:

```
GET /api/assets/{id}/original
```

Returns the original binary data (video file). Used by ExoPlayer when
Skip Videos is off. Requires the `asset.download` permission (distinct from
`asset.view`, which only covers thumbnails/previews).

URL construction is in `ImmichRepositoryImpl.kt`:
- `imageUrl(assetId)` → `{base}/api/assets/{id}/thumbnail?size=preview&apiKey={key}`
- `thumbnailUrl(assetId)` → `{base}/api/assets/{id}/thumbnail?size=thumbnail&apiKey={key}`
- `videoUrl(assetId)` → `{base}/api/assets/{id}/original?apiKey={key}`

## Rate Limiting

Immich does not impose explicit rate limits on API requests. However, the
slideshow prefetcher should be reasonable — prefetching 3 images ahead
at 30s intervals is well within normal usage.

## API Versioning

Immich follows semantic versioning. The API endpoints listed above are
marked as `Stable` in the Immich API docs, meaning they should not break
across minor version updates.

If Immich changes their API, the app will surface HTTP errors (404, 400)
rather than crash. Version compatibility can be checked via
`GET /api/server-info/version` if needed in the future.

## GitHub API (Self-Update)

The self-update feature uses the GitHub API (not Immich):

```
GET https://api.github.com/repos/dave-palt/immich-photo-frame/releases/latest
```

No authentication required (public repo). Returns the latest release with
tag name (`dev-{sha}` format), assets (APK download URL), and release notes.

Implemented in `GitHubApi.kt` / `GitHubDtos.kt` / `UpdateManager.kt`.
