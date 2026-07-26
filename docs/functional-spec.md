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

1. App loads assets for selected album(s). **Cache-first**: if assets are
   already cached locally (Room database), they're displayed immediately
   without network access. On a cold start (empty cache), the app fetches
   asset metadata from the server via `POST /search/metadata`. If **Auto
   Sync** is enabled, a background WorkManager job downloads new/updated
   assets and reconciles deletions for the next launch.
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
11. **Photo Animations**: When enabled, each photo gets a subtle Ken Burns
    style animation (zoom/pan). The animation is chosen randomly from the
    set of individually-enabled animation types. Available types: Zoom In,
    Zoom Out, Pan Left, Pan Right, Pan Up, Pan Down, Random. Random picks
    from the other enabled types and requires at least one other type enabled.

### F4: In-Slideshow Controls

Tap the screen to reveal controls:
- Previous / Next arrows
- Pause / Play
- Photo count (current position / total)
- **Media Selection** (grid icon, next to the photo count) — biometric-gated;
  opens a grid of all album media where the user can tap thumbnails to
  show/hide them from the slideshow (see F8)
- Update status icon (checking / downloading / ready)
- Launcher switch (apps icon, when Launcher Mode is active)
- Albums (photo library icon)
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
- **Clock drift**: When photo animations are enabled, the clock also drifts slightly
  (±4 px horizontal over 30s, ±3 px vertical over 45s) to prevent OLED burn-in.

### F5: Subsequent Launch (Auto-Resume)

1. App checks for stored credentials and selected album IDs.
2. If both exist, skip setup and album selection.
3. Go directly to slideshow (fetch album assets, start playing).
4. If credentials are invalid (API returns 401), fall back to setup screen.

### F5b: Start on Boot

When **Start on Boot** is enabled, the app launches automatically when the device
boots. On Android 10+ (API 29+), the OS blocks starting an Activity from a
background `BroadcastReceiver` (Background Activity Launch restriction) unless
the app holds the `SYSTEM_ALERT_WINDOW` ("Display over other apps") permission.
The app prompts the user to grant this permission when the feature is toggled on,
and shows a "Grant Display Over Other Apps" button in Settings until it is
granted. On certain OEMs (Xiaomi, Oppo, Vivo, Huawei, Honor, etc.) that further
restrict autostart, the app detects the manufacturer and prompts the user to
grant the autostart permission, deep-linking to the correct system settings
screen.

The app self-tests whether the BootReceiver actually fired after a reboot by
tracking a `bootVerified` flag. Toggling **Start on Boot** resets this flag to
false. After each reboot, the BootReceiver sets it to true (if it received the
broadcast). In Settings, the "Open Autostart Settings" button is shown only when
the feature is on, the device is a restricted OEM, and the flag is false — i.e.,
the receiver hasn't proven itself yet. Once verified by a successful reboot, the
button disappears and a normal description is shown.

### F5c: Launcher Mode (Home Replacement)

When **Launcher Mode** is enabled, the app registers itself as a Home launcher
by enabling an `activity-alias` in the manifest via
`PackageManager.setComponentEnabledSetting()`. The system always launches the
default Home app on boot and on Home-button press — no BOOT_COMPLETED broadcast,
no autostart permission, and no Background Activity Launch restriction applies.
This is the **most reliable autostart method**, especially on Chinese OEM ROMs
(OPPO/Realme/Xiaomi/etc.) that silently block boot broadcasts to non-whitelisted
apps, and works around the known Android 15/16 BOOT_COMPLETED delivery bug.

When Launcher Mode is on, the app shows an **"Open Launcher Settings"** button in
Settings that opens the system Home settings page
(`ACTION_HOME_SETTINGS`), allowing the user to switch to a different launcher
or re-select this app. The same button is available in the slideshow hover UI
(top bar, apps icon) when launcher mode is active, so the user can switch
launchers without navigating to settings.

If the app loses its default-launcher status while Launcher Mode is enabled
(e.g., the user selected another launcher), a dialog appears on resume
prompting the user to re-select Immich Media Frame as the default Home.
Toggling Launcher Mode off disables the alias and reverts to normal
behaviour.

### F5d: Self-Update via GitHub Releases

On startup (if **Auto-Update** is enabled and the app was NOT installed from the
Play Store), the app checks GitHub for a new release:

- **Release builds** (the primary auto-update target) fetch
  `/releases/latest` and compare the tag (`vX.Y.Z`) against the installed
  `versionName` using semantic version comparison. If the remote version is
  newer, the APK is downloaded.
- **Debug builds** (dev channel) list recent releases, filter to `dev-{sha}`
  tags, sort by `created_at` descending, and compare the newest tag's SHA
  against `BuildConfig.GIT_SHA`.

The downloaded APK is stored in the app's cache directory. An update status
icon appears in the slideshow top bar (visible when controls are toggled on):
spinner while **checking**, a circular progress ring with live percentage while
**downloading** (tap for a tooltip with ETA), red icon on **error**, and a
highlighted icon when **ready to install**. The icon is hidden entirely when no
update is available and nothing is in progress. Tapping the icon when ready
opens the install dialog (**Install** / **Later**), which can be re-triggered at
any time.

Downloads support HTTP Range-based resume: if a partial APK of the same version
already exists in cache, the download resumes from where it left off rather than
restarting. Old APKs from different versions are deleted before each new
download.

On the first install attempt, the app checks for the
**"Install unknown apps"** permission (`REQUEST_INSTALL_PACKAGES`). If not
granted, it opens the system settings page for the user to enable it before
launching the package installer.

The **Auto-Update** toggle and **Check Now** button are hidden when the app
is installed from the Play Store — Play Store installs receive updates through
the Play Store, not self-update.

### F6: Settings

Accessible from:
- Setup screen (before slideshow starts)
- In-slideshow controls (gear icon)

Options:
- **Slideshow Interval** — seconds per image (5–120, default 30)
- **Image Fit** — Contain (letterbox) or Cover (crop to fill)
- **Adaptive Background** — fill letterbox bars with dominant color from each
  photo (uses Palette API, default off)
- **Shuffle** — randomize image order (default on)
- **Skip Videos** — only show photos (default on)
- **Muted** — silence video audio (default on)
- **Photo Animations** — subtle Ken Burns zoom/pan on each photo (default off).
  Also serves as burn-in protection for always-on displays. When enabled,
  reveals individual toggles for: Zoom In, Zoom Out, Pan Left,
  Pan Right, Pan Up, Pan Down, Random. Random picks from other enabled types
  and requires at least one other enabled.
- **Fullscreen** — hide system bars (default on)
- **Keep Screen On** — wake lock toggle (default on)
- **Start on Boot** — launch on device boot (default off). Requires the "Display over other apps" permission (Android 10+ BAL exemption); on Chinese OEMs, also shows an "Open Autostart Settings" button until a reboot confirms the receiver fired.
- **Launcher Mode** — register as a Home launcher (default off; only visible when Start on Boot is enabled). The most reliable autostart method; the system always launches the Home app on boot, bypassing BOOT_COMPLETED and OEM autostart blocks entirely. Shows an "Open Launcher Settings" button to switch launchers or re-select this app; the same action is available in the slideshow hover UI.
- **Auto-Update** — check GitHub for new builds (default on, hidden if Play
  Store installed). A **"Check Now"** button below it triggers an immediate
  update check regardless of the toggle state.
- **Media Cache** section:
  - **Auto Sync** — automatically download new photos and remove deleted
    ones in the background (default on)
  - **Sync Interval** — how often to check for album changes, 1 min or 5–480 min
    in steps of 5 (default 30; clamped to 15 min minimum by WorkManager)
  - **Sync Now** — trigger an immediate one-time sync
- **Clock** section:
  - **Show Clock** — display time overlay (default off)
  - **Clock Size** — slider 24–96 sp (default 48)
  - **Snap to Grid** — align clock to grid on release (default on)
- **Connection** section:
  - **Server URL** — editable inline
  - **API Key** — editable inline. For security, tapping **Edit** empties
    the field (the key is never pre-populated); the user must re-type it.
    When the key is set, two biometric-gated buttons appear:
    **Reveal** (shows the key in monospace; tap again to hide) and
    **Copy** (copies to clipboard, then auto-hides). Both require
    fingerprint / face / device-PIN authentication. If no screen lock
    is set up, a dialog prompts the user to create one.
  - Test Connection button
- **Albums** — change album selection (returns to album picker)
- **Reset All Settings** — clears everything, returns to setup screen

### F8: Media Selection

Accessible from the slideshow top bar (grid icon, next to the photo count).
Requires biometric / device-credential authentication to open.

1. User taps the grid icon in the slideshow controls.
2. Biometric prompt appears (fingerprint / face / device PIN). If the user
   cancels, nothing happens. If no screen lock is set up, a dialog directs
   them to security settings.
3. On success, a grid of all album assets loads (cache-first, thumbnails via
   Coil). Each thumbnail shows:
   - A checkmark badge (white = shown, dimmed = hidden)
   - A video badge for video assets
   - A dim overlay on hidden assets
4. Tapping a thumbnail toggles its shown/hidden state. Changes persist
   immediately to DataStore and take effect the next time the slideshow
   loads.
5. A **"Show new photos by default"** switch at the top controls the mode:
   - **ON** (default): all media starts shown; tapping hides individual items.
     New photos added to the album later appear automatically.
   - **OFF**: all media starts hidden; tapping shows individual items. New
     photos added later are hidden until manually shown.
   Flipping the switch preserves the current visible selection — it only
   changes the default for future new media.
6. **Show All** / **Hide All** buttons provide bulk selection.
7. A counter shows "X of Y shown" in the top bar.
8. Back arrow returns to the slideshow.

## Error Handling

| Scenario | Behavior |
|---|---|
| Server unreachable | Show retry button with error detail |
| API key invalid (401) | Show "API key rejected", return to setup |
| Album has no images | Skip album, show toast notification |
| Image fails to load | Skip to next image, log error |
| Update download fails | Show "Update check failed" on Check Now button, allow retry |
| Network timeout | Retry up to 3 times with backoff, then skip |

### F7: Onboarding Tour

The app includes a **modular, per-step onboarding tour** that teaches users
the available settings and controls. The tour is triggered automatically when
a user lands on a screen — only steps not yet completed are shown.

**How it works:**

- Each screen (Setup, Albums, Slideshow, Settings) declares an ordered list
  of tour steps. Each step has an ID, a tooltip title/body, and an optional
  target element (a control or section to spotlight).
- When the user navigates to a screen, the tour checks which of that screen's
  step IDs are NOT yet in the persisted `onboarding_completed_steps` set. If
  any remain, the tour auto-starts for those steps.
- The overlay shows a semi-transparent scrim over the screen with a
  rounded-rect spotlight cutout around the target element (if any). A tooltip
  card displays the step title, body, step counter ("Step X of Y"), and
  **Next/Got it** + **Skip** buttons.
- Completing a step (Next/Got it) marks its ID as completed. Skipping marks
  all remaining steps on that screen as completed.
- **Slideshow-specific behavior**: during the tour, the on-tap controls are
  force-shown and the 5-second auto-hide is suppressed, so the tour can
  spotlight the prev/next, pause/mute, settings, and close buttons.
- **Settings-specific behavior**: the tour scrolls the target section into
  view before showing its spotlight (System → Media Cache → Connection).
- **Two replay buttons** in Settings → System section:
  - **"Show Tour Again"** clears only the Settings screen's step IDs, so
    only the Settings tour re-runs.
  - **"Reset All Tours"** clears the entire completed-steps set, so the
    tour re-runs on every screen next visit.
- A **"Show Tour Again"** button is also available on the Setup screen.
- Resetting all settings also clears the onboarding set (DataStore is wiped).

**Step inventory (19 steps across 4 screens):**

| Screen | Steps |
|---|---|
| Setup (4) | `setup_welcome` (centered), `setup_server`, `setup_apikey`, `setup_connect` |
| Albums (3) | `albums_select`, `albums_start`, `albums_settings` |
| Slideshow (8) | `slideshow_tap` (centered), `slideshow_nav`, `slideshow_playback`, `slideshow_media_selection`, `slideshow_albums`, `slideshow_update`, `slideshow_settings`, `slideshow_close` |
| Settings (4) | `settings_overview` (centered), `settings_system`, `settings_cache`, `settings_connection` |

- **`slideshow_media_selection`**: highlights the grid icon next to the photo
  count. The tooltip explains that this opens the biometric-gated media
  selection grid where the user can choose which photos and videos appear.

- **`slideshow_albums`**: highlights the back-to-album-selection button.
- **`slideshow_update`**: highlights the update status icon. Since this icon
  normally only appears when an update is available, the tour force-shows a
  dimmed placeholder icon so the coachmark always has a visible target on the
  first run. The tooltip explains that the icon's presence means an update is
  available.
