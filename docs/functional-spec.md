# Functional Specification

## User Flows

### F1: First-Run Setup

1. App launches with no stored credentials.
2. Setup screen prompts for:
   - Immich Server URL (e.g. `https://photos.example.com` or `http://192.168.1.100:2283`)
   - API Key
3. User taps "Test Connection".
4. App calls `GET /server/ping` then `GET /users/me` to validate.
   - On failure: show error message (wrong URL, unreachable, invalid key). Stay on setup screen.
   - On success: store credentials, proceed to album selection.
5. Credentials persisted to encrypted on-device storage.

### F2: Album Selection

1. App calls `GET /albums` to fetch the user's album list.
2. Display albums as a scrollable grid with:
   - Album thumbnail (first asset preview)
   - Album name
   - Asset count
3. User taps one or more albums (multi-select).
4. User taps "Start Slideshow".
5. Selected album IDs persisted to DataStore.
6. Slideshow begins.

### F3: Slideshow Playback

1. App fetches assets for selected album(s) via `GET /albums/{id}`.
2. If multiple albums selected, asset lists are merged and shuffled.
3. Slideshow displays each image fullscreen for the configured interval (default 30s).
4. Transition between images is a crossfade (default 1s).
5. Next image is pre-fetched and cached so transitions are instant.
6. When the last image is reached, the slideshow loops back to the first.
7. Screen stays on (wake lock) while slideshow is active.

### F4: In-Slideshow Controls

Tap the screen to reveal controls:
- Previous / Next arrows
- Pause / Play
- Album name (current)
- Settings (gear icon)
- Close slideshow (return to album selection)

Controls auto-hide after 5 seconds of no interaction.

### F5: Subsequent Launch (Auto-Resume)

1. App checks for stored credentials and selected album IDs.
2. If both exist, skip setup and album selection.
3. Go directly to slideshow (fetch album assets, start playing).
4. If credentials are invalid (API returns 401), fall back to setup screen.

### F6: Settings

Accessible from:
- Setup screen (before slideshow starts)
- In-slideshow controls (gear icon)

Options:
- **Server URL** — editable, with "Test Connection" button
- **API Key** — editable, masked
- **Selected Albums** — change album selection (returns to album picker)
- **Slideshow Interval** — seconds per image (5–300, default 30)
- **Transition Duration** — crossfade duration (0–3s, default 1s)
- **Image Fill Mode** — Contain (letterbox) or Cover (crop to fill)
- **Ken Burns Effect** — slow zoom/pan on/off (default off)
- **Show Clock** — display current time overlay (default off)
- **Keep Screen On** — wake lock toggle (default on)

## Error Handling

| Scenario | Behavior |
|---|---|
| Server unreachable | Show retry button with error detail |
| API key invalid (401) | Show "API key rejected", return to setup |
| Album has no images | Skip album, show toast notification |
| Image fails to load | Skip to next image, log error |
| Network timeout | Retry up to 3 times with backoff, then skip |
