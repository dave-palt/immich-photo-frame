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

## Media URL Routing

| Asset type | Endpoint | Notes |
|---|---|---|
| Standard image | `GET /api/assets/{id}/thumbnail?size=preview` | Immich transcodes to JPEG preview; small and fast. Auth: `?apiKey=` query param. |
| Animated GIF | `GET /api/assets/{id}/original` | The thumbnail endpoint re-encodes GIFs as JPEG, collapsing the animation. GIFs are detected by `originalMimeType == "image/gif"` (from the `/search/metadata` response) and routed to `/original` so Coil's `GifDecoder` receives the raw animated bytes. |
| Video | `GET /api/assets/{id}/original` | Loaded by ExoPlayer. Auth: `?apiKey=` query param. |
| Thumbnail (media-selection grid) | `GET /api/assets/{id}/thumbnail?size=thumbnail` | Always JPEG — Coil can't decode a video file into a still, and GIF thumbnails are fine as static images. |

API keys can be created in Immich under User Settings > API Keys, and can be
scoped to specific permissions.

**In-app key generation**: during setup, the default is to paste an existing
key manually. A **Generate Key** helper button lets the user auto-create a
scoped key via email/password (or OAuth) — the app logs in, creates the key,
and discards the password. See F1 in the functional spec for details.

### Required Permissions

The API key needs 5 scoped permissions:

| Permission | Used for |
|---|---|
| `album.read` | List albums, get album info |
| `asset.read` | Search/list assets in albums (POST /search/metadata) |
| `asset.view` | View thumbnails and previews (images via `?apiKey=`) |
| `asset.download` | Download original files (video playback via ExoPlayer) |
| `user.read` | Validate API key (GET /users/me) |

These permissions are the minimum required for ImmichFrame to function.
The in-app key generator creates a key with exactly these permissions
(requires Immich v1.135+ for scoped keys). The external `keymgr` scripts
are still available as a fallback.

### Permission Verification

After the API key is stored (whether generated in-app or pasted manually),
the app probes each required endpoint to verify the key actually has the
necessary scopes. This mirrors the external `scripts/check-api-key.sh`
script. Probes run in dependency order:

| Step | Endpoint | Permission Tested | Auth method | Notes |
|---|---|---|---|---|
| 1 | `GET /api/users/me` | `user.read` | `x-api-key` header | Also validates the key itself |
| 2 | `GET /api/albums` | `album.read` | `x-api-key` header | Returns album list |
| 3 | `POST /api/search/metadata` | `asset.read` | `x-api-key` header | Returns first asset ID for downstream probes |
| 4 | `GET /api/assets/{id}/thumbnail?size=preview` | `asset.view` | `?apiKey=` query param | **Same auth path as Coil image loading** — probed via OkHttp, not Retrofit, because the real app never loads media through the header |
| 5 | `GET /api/assets/{id}/original` | `asset.download` | `?apiKey=` query param | **Same auth path as ExoPlayer video loading** — Optional; gates video playback + media cache |

> **Why two auth methods?** Steps 4–5 use the `?apiKey=` query parameter
> (not the `x-api-key` header) because that is exactly how Coil (images) and
> ExoPlayer (videos) authenticate when loading media in the app. Probing with
> the header would test a code path the app never actually uses — and on some
> Immich deployments the two auth methods are handled differently for scoped
> keys, causing a false "missing asset.view" report despite images loading
> fine. Using the query param makes the probe reflect reality.

If an upstream probe fails, downstream probes are skipped and marked
"unknown" (not "denied"). Results are stored as `permission_status` (JSON)
in DataStore and refreshed every time the Settings screen opens or the
user taps "Re-check".

## In-App Auth Endpoints

These endpoints are used during setup by a separate Retrofit instance
(`ImmichAuthApi`) that does NOT use the `x-api-key` header. Login and
key-creation calls use `Bearer` tokens passed per-call via the
`Authorization` header.

### Server Probing (no auth)

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/server/version` | Detect Immich version (scoped-key support check) |
| GET | `/server/features` | Detect available auth methods (passwordLogin, oauth) |

### Password Login (no auth)

| Method | Endpoint | Request | Response |
|---|---|---|---|
| POST | `/auth/login` | `{ email, password }` | `{ accessToken, userId, userEmail, ... }` |

### API Key Management (Bearer token)

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api-keys` | List existing keys (metadata only, no secrets) |
| POST | `/api-keys` | Create a new key: `{ name: "ImmichMediaFrame", permissions: [...] }` → returns `{ secret }` |
| PUT | `/api-keys/{id}` | Update key name/permissions (metadata only) |
| POST | `/auth/logout` | Invalidate the bearer session after key creation so the device doesn't linger in "Authorized Devices". The API key survives logout. |

### OAuth PKCE (no auth)

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/oauth/authorize` | `{ redirectUri, codeChallenge, state }` → `{ url }` (open in browser) |
| POST | `/oauth/callback` | `{ url, codeVerifier, state }` → `{ accessToken, ... }` (same as login) |

The OAuth flow uses PKCE: the app generates a `code_verifier` + `code_challenge`
+ `state` locally (`PkceHelper.kt`), opens the authorization URL in a Custom
Tab, and receives the callback via the `com.dav3.immichframe://oauth-callback`
deep link.

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

## Weather API (Open-Meteo)

The weather overlay uses the [Open-Meteo](https://open-meteo.com) API — free,
no API key required, no auth header. Calls are made directly from the Android
client via a dedicated Retrofit instance (`WeatherRepositoryImpl`) that does
NOT use the `x-api-key` header.

```
GET https://api.open-meteo.com/v1/forecast
    ?latitude={lat}
    &longitude={lon}
    &current=temperature_2m,weather_code
    &temperature_unit={celsius|fahrenheit}
    &timezone=auto
```

Response (abbreviated):
```json
{
  "current": {
    "temperature_2m": 21.3,
    "weather_code": 2
  }
}
```

WMO weather codes are mapped to human-readable descriptions client-side
(`wmoWeatherDescription()` in `WeatherData.kt`). Weather is fetched on
slideshow entry and refreshed every 10 minutes.

## OSM Nominatim (Address Search + Reverse Geocode)

The weather location picker uses [OpenStreetMap
Nominatim](https://nominatim.openstreetmap.org/) for free-text address
search and reverse-geocoding GPS coordinates to a friendly label. No API
key required.

**Address search** (when the user types a query):
```
GET https://nominatim.openstreetmap.org/search
    ?format=json&q={query}&limit=5&addressdetails=0
```

**Reverse geocode** (after a GPS fix):
```
GET https://nominatim.openstreetmap.org/reverse
    ?format=json&lat={lat}&lon={lon}&zoom=14&addressdetails=0
```

Both calls send `User-Agent: ImmichFrame/1.0 (photo-frame app)` per
Nominatim's usage policy. Calls are user-initiated (button tap) — no
background polling, well within rate limits.

## GitHub API (Self-Update)

The self-update feature uses the GitHub API (not Immich). The update path
depends on build type:

**Release builds** (primary auto-update target):
```
GET https://api.github.com/repos/dave-palt/immich-photo-frame/releases/latest
```
Returns the latest non-prerelease (tag format `vX.Y.Z`). The app compares
the tag against `BuildConfig.VERSION_NAME` using semantic version comparison.
If newer, the APK asset is downloaded and the system installer invoked.

**Debug builds** (dev channel):
```
GET https://api.github.com/repos/dave-palt/immich-photo-frame/releases?per_page=30
```
Lists recent releases (including prereleases). The newest `dev-{sha}` tag is
selected and its SHA compared against `BuildConfig.GIT_SHA`. If different,
the APK is downloaded and installed.

No authentication required (public repo) for either path. Self-update is
disabled entirely for Play Store installs — those receive updates via the
Play Store.

Implemented in `GitHubApi.kt` / `GitHubDtos.kt` / `UpdateManager.kt`.
