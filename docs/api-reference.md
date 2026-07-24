# Immich API Reference

Base URL: user-provided (e.g. `https://photos.example.com:2283` or `http://192.168.1.100:2283`)

All endpoints are under `/api` prefix.

## Authentication

Send the API key as an HTTP header:

```
x-api-key: <apiKey>
```

Alternatively as a query parameter: `?apiKey=<apiKey>` (not used by this app).

API keys can be created in Immich under User Settings > API Keys, and can be
scoped to specific permissions.

### Required Permissions

For v1 (album browsing + image display), the API key needs:

| Permission | Used for |
|---|---|
| `album.read` | List albums, get album info |
| `asset.read` | Download/view assets (images) |

## Endpoints Used

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

Optional query params:
- `assetId` — (none) list albums containing a specific asset

Returns array of albums:

```json
[
  {
    "id": "album-uuid",
    "ownerId": "user-uuid",
    "albumName": "Vacation 2024",
    "description": "",
    "assetCount": 142,
    "lastModifiedAssetTimestamp": "2024-12-01T...",
    "startDate": "2024-06-01T...",
    "endDate": "2024-06-15T...",
    "hasSharedLink": false,
    "order": "default",
    "albumThumbnailAssetId": "asset-uuid"
  }
]
```

---

### 4. Get Album Details (with assets)

```
GET /api/albums/{id}
```

Auth: `x-api-key`

Optional query params:
- `withoutAssets` — if `true`, returns album info without the asset list

Returns album with full asset list:

```json
{
  "id": "album-uuid",
  "albumName": "Vacation 2024",
  "assetCount": 142,
  "assets": [
    {
      "id": "asset-uuid",
      "deviceAssetId": "1",
      "ownerId": "user-uuid",
      "deviceId": "device",
      "originalPath": "/upload/...",
      "type": "IMAGE",
      "originalFileName": "IMG_0001",
      "originalMimeType": "image/jpeg",
      "exifInfo": { ... },
      "isFavorite": false,
      "isArchived": false,
      "localDateTime": "2024-06-01T...",
      "thumbhash": "base64string"
      // many more fields available
    }
  ]
}
```

For the slideshow, we extract `id` and `type` from each asset. We filter
to `type == "IMAGE"` (skip videos in v1).

---

### 5. Download / View Asset

```
GET /api/assets/{id}/original
```

Auth: `x-api-key`

Returns the binary image data (JPEG, PNG, etc.).

For slideshow display, we use the preview endpoint instead (smaller, faster):

```
GET /api/assets/{id}/thumbnail?size=preview
```

Returns a web-friendly preview image. The `size` parameter accepts:
- `thumbnail` — small (~100px)
- `preview` — medium/large (~1440p), good for slideshow display
- `full` — original resolution

The app requests `size=preview` for all slideshow images to optimize
bandwidth and disk cache usage.

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
