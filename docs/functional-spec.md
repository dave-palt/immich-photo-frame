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

1. App fetches assets for selected album(s) via `POST /search/metadata` (searching
   by album ID). This replaces the older `GET /albums/{id}` approach used in v1,
   which does not work reliably in Immich v3.
2. If multiple albums selected, asset lists are merged.
3. If **Shuffle** is enabled (default on), the merged list is randomized.
4. If **Skip Videos** is enabled (default on), video assets are filtered out.
   When disabled, video assets are played inline via ExoPlayer (Media3), with
   optional mute and automatic advance on completion.
5. Slideshow displays each image fullscreen for the configured interval (default 30s).
6. Transition between images is a crossfade (default 1s).
7. Next image is pre-fetched and cached so transitions are instant.
8. When the last image is reached, the slideshow loops back to the first.
9. Screen stays on (wake lock) while slideshow is active (toggleable).
10. A progress bar at the bottom shows time remaining for the current image.

### F4: In-Slideshow Controls

Tap the screen to reveal controls:
- Previous / Next arrows
- Pause / Play
- Album name (current)
- Settings (gear icon)
- Close slideshow (return to album selection)

Controls auto-hide after 5 seconds of no interaction.

### F4b: Clock Overlay

When **Show Clock** is enabled, a clock is displayed on top of the slideshow.

- **Position**: Long-press and drag the clock to reposition it anywhere on screen.
  Position is stored as normalized coordinates (0.0–1.0) and persists across launches.
- **Size**: Configurable via slider (24–96 sp).
- **Snap to Grid**: When enabled (default on), the clock snaps to a grid based on
  its font size when released after dragging.
- **Burn-in drift**: When burn-in protection is on, the clock also drifts slightly
  (±4 px horizontal over 30s, ±3 px vertical over 45s) to prevent OLED burn-in.

### F5: Subsequent Launch (Auto-Resume)

1. App checks for stored credentials and selected album IDs.
2. If both exist, skip setup and album selection.
3. Go directly to slideshow (fetch album assets, start playing).
4. If credentials are invalid (API returns 401), fall back to setup screen.

### F5b: Start on Boot

When **Start on Boot** is enabled, the app launches automatically when the device
boots. On certain OEMs (Xiaomi, Oppo, Vivo, Huawei, Honor, etc.) that restrict
autostart, the app detects the manufacturer and prompts the user to grant the
autostart permission, deep-linking to the correct system settings screen.

### F5c: Self-Update via GitHub Releases

On startup (if **Auto-Update** is enabled and the app was NOT installed from the
Play Store), the app checks `api.github.com/repos/dave-palt/immich-photo-frame/releases/latest`
for a new release. If the release tag SHA differs from the build's `GIT_SHA`, the
APK is downloaded to the app's cache and the system package installer is invoked.
The user sees the standard Android "Install?" dialog but stays in-app.

This setting is hidden when the app is installed from the Play Store.

### F6: Settings

Accessible from:
- Setup screen (before slideshow starts)
- In-slideshow controls (gear icon)

Options:
- **Slideshow Interval** — seconds per image (5–120, default 30)
- **Burn-in Protection** — slow zoom/pan on images (default off, warning shown
  if interval ≥60s and off)
- **Image Fit** — Contain (letterbox) or Cover (crop to fill)
- **Adaptive Background** — fill letterbox bars with dominant color from each
  photo (uses Palette API, default off)
- **Shuffle** — randomize image order (default on)
- **Skip Videos** — only show photos (default on)
- **Muted** — silence video audio (default on)
- **Fullscreen** — hide system bars (default on)
- **Keep Screen On** — wake lock toggle (default on)
- **Start on Boot** — launch on device boot (default off)
- **Auto-Update** — check GitHub for new builds (default on, hidden if Play
  Store installed)
- **Clock** section:
  - **Show Clock** — display time overlay (default off)
  - **Clock Size** — slider 24–96 sp (default 48)
  - **Snap to Grid** — align clock to grid on release (default on)
- **Connection** section:
  - **Server URL** — editable inline
  - **API Key** — editable inline, masked
  - Test Connection button
- **Albums** — change album selection (returns to album picker)
- **Reset All Settings** — clears everything, returns to setup screen

## Error Handling

| Scenario | Behavior |
|---|---|
| Server unreachable | Show retry button with error detail |
| API key invalid (401) | Show "API key rejected", return to setup |
| Album has no images | Skip album, show toast notification |
| Image fails to load | Skip to next image, log error |
| Network timeout | Retry up to 3 times with backoff, then skip |
| Update download fails | Show error in update dialog, allow retry |
