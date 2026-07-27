# UI Specification

Material 3 design. Dark theme by default (photo frame context — images
look better against dark background). Light theme available as a setting.

## Screens

### 1. Setup Screen

Shown on first launch or when credentials are missing/invalid.

**Step 1: Domain Validation**

```
┌──────────────────────────────┐
│                              │
│         [App Logo]           │ ← app logo vector (120dp)
│                              │
│   ImmichFrame                │
│   Connect to your server     │
│                              │
│   [https:// ▼] [domain.....] │ ← protocol dropdown + URL field
│   photos.example.com         │
│                              │
│   ┌──────────────────────┐   │
│   │   Validate Server    │   │
│   └──────────────────────┘   │
│                              │
│   Status: [idle/connecting/  │
│            error]            │
│                              │
│   [Show Tour Again]          │
└──────────────────────────────┘
```

**Step 2: Authentication (after domain validated)**

```
┌──────────────────────────────┐
│         [App Logo]           │
│                              │
│   Immich v1.135.0            │ ← detected server version
│   photos.example.com         │
│                              │
│   [Generate Key] [Manual]    │ ← auth mode toggle chips
│                              │
│   ── Generate Key mode ──    │
│   Generate API Key      [?]  │ ← help icon → dialog
│   Log in with email/password │
│   ┌────────────────────────┐ │
│   │ Email                  │ │
│   └────────────────────────┘ │
│   ┌────────────────────────┐ │
│   │ Password          [👁] │ │
│   └────────────────────────┘ │
│   ┌──────────────────────┐   │
│   │ Log In & Generate    │   │
│   └──────────────────────┘   │
│   ┌──────────────────────┐   │
│   │ Sign in with OAuth   │   │ ← only if OAuth enabled
│   └──────────────────────┘   │
│                              │
│   [← Back]                   │
└──────────────────────────────┘
```

- URL field: protocol dropdown (`https://` / `http://`) + domain field. Auto-normalizes.
- "Validate Server" calls `GET /server/version` + `GET /server/features` (no auth).
  On success, shows detected version and advances to the auth step.
- Auth mode: **manual paste is the default**. API key field + Test Connection
  are shown first. Below them, a divider + helper text + subtle TextButton
  ("✨ Generate Key") offers auto-generation.
- **Generate Key** mode (via helper button): email + password fields, with an
  italic note: "Use the account meant for this photo frame — it doesn't have
  to be your personal account." `?` icon opens a dialog explaining API keys.
- **OAuth** button appears only when the server has OAuth enabled.
- "Enter Manually" text link at the bottom switches back to paste mode.
- Status area shows idle / connecting / success / error states.
- On success, auto-navigates to album selection after brief delay.

### 2. Album Selection Screen

```
┌──────────────────────────────┐
│  Select Albums         [⚙]   │
├──────────────────────────────┤
│  ┌──────┐  ┌──────┐         │
│  │ [img]│  │ [img]│  ...    │
│  │      │  │      │         │
│  ├──────┤  ├──────┤         │
│  │✓ Trips│  │ Family│       │
│  │ 142   │  │ 89    │       │
│  └──────┘  └──────┘         │
│                              │
│  ┌──────┐  ┌──────┐         │
│  │ [img]│  │ [img]│         │
│  ...                         │
├──────────────────────────────┤
│  2 albums selected           │
│  ┌──────────────────────┐    │
│  │   Start Slideshow    │    │
│  └──────────────────────┘    │
└──────────────────────────────┘
```

- LazyVerticalGrid (2-3 columns depending on screen width).
- Each card shows album thumbnail (preview of `albumThumbnailAssetId`),
  album name, asset count.
- Tap to toggle selection (checkbox or border highlight).
- Multi-select: more than one album can be selected.
- Bottom bar shows count of selected albums + Start button.
- Start button disabled if no albums selected.
- Settings gear in top app bar.
- Loading state: shimmer placeholders while album list loads.
- **Empty states**:
  - Server reachable but zero albums: centered photo-library icon,
    "No albums available" title, explanation text, Retry button.
  - Server unreachable: centered error text + Retry button.

### 3. Slideshow Screen

```
┌──────────────────────────────┐
│                              │
│                              │
│                              │
│        [ Full Image ]        │
│                    [13:37]   │ ← clock overlay (draggable)
│                              │
│                              │
│                              │
│  ░░░░░░░░░░░░░░░░░░░░░░░░░░  │ ← progress bar (time remaining)
└──────────────────────────────┘
```

Fullscreen, immersive mode (status bar + nav bar hidden).

- Clock overlay appears when enabled. Long-press and drag to reposition.
- Progress bar at the bottom shows elapsed/remaining time for current image.
- Video assets (when Skip Videos is off) play inline with ExoPlayer.

**On tap** — overlay controls fade in:

```
┌──────────────────────────────┐
│ [Album Name]    [⬇/⟳/⬆] [⚙] [✕]│
├──────────────────────────────┤
│                              │
│                              │
│        [ Full Image ]        │
│  ◀                         ▶ │
│                              │
│                              │
├──────────────────────────────┤
│         [ ⏸ Pause ]          │
└──────────────────────────────┤
5s inactivity → controls fade out
```

- Previous/Next arrows on left/right edges.
- Pause/Play button at bottom center.
- Album name top-left, Settings + Close top-right.
- **Update status icon** (left of Settings): hidden when idle; spinner while
  checking, circular progress ring with percentage while downloading (tap →
  tooltip with ETA), red on error, highlighted when ready to install. Tap
  when ready opens the install dialog.
- Controls are semi-transparent overlay, do not push image.
- Controls auto-hide after 5 seconds.
- Immersive flag re-engages when controls hide.

### 4. Settings Screen

Accessible from album selection (gear icon) or slideshow controls (gear icon).

```
┌──────────────────────────────┐
│  ← Settings v1.2.0           │
├──────────────────────────────┤
│                              │
│  SLIDESHOW                   │
│  Interval: 30s          [───]│ ← slider 5-120s
│                              │
│  IMAGE                       │
│  Fill: [Contain] [Cover]     │ ← filter chips
│  Adaptive Background    [○]  │ ← toggle (letterbox color fill)
│                              │
│  Shuffle                [●]  │ ← toggle
│  Skip Videos            [●]  │ ← toggle
│  Muted                  [●]  │ ← toggle
│  Photo Animations       [○]  │ ← toggle (expandable)
│  ┌─ Zoom In          [●]  ┐  │ ← shown when animations on
│  │  Zoom Out         [○]  │  │
│  │  Pan Left         [○]  │  │
│  │  Pan Right        [●]  │  │
│  │  Pan Up           [○]  │  │
│  │  Pan Down         [○]  │  │
│  │  Random           [●]  │  │
│  └────────────────────────┘  │
│                              │
│  Fullscreen             [●]  │ ← toggle
│  Keep Screen On         [●]  │ ← toggle
│                              │
│  NIGHT MODE                  │ ← section
│  Night Mode             [○]  │ ← toggle
│  "Prefer device's built-in   │ ← helper text (last-resort note)
│   scheduled power on/off"    │
│  Dim screen at          22:00│ ← time picker dialog
│  Brighten screen at     07:00│ ← time picker dialog
│  Night brightness: 0%       │
│  [●─────────────────────]   │ ← slider 0-100%
│                              │
│  Start on Boot          [○]  │ ← toggle (+ overlay & OEM autostart prompts)
│  Launcher Mode          [○]  │ ← toggle (+ Open Launcher Settings button)
│  Auto-Update            [●]  │ ← toggle (hidden if Play Store)
│  Check Now                   │ ← button: shows status while active
│                              │     ("Checking…", "Downloading…", "Update check failed")
│                              │
│  MEDIA CACHE                 │
│  Auto Sync              [●]  │ ← toggle
│  Sync Interval: 30 min      │
│  [────────●──────────────]  │ ← slider (1, 5, 10, ... 480, step 5)
│  ┌──────────────────────┐    │
│  │     Sync Now         │    │ ← button → one-time sync
│  └──────────────────────┘    │
│                              │
│  CLOCK                       │
│  Show Clock             [○]  │ ← toggle
│  ┌────────────────────────┐  │
│  │      13:37             │  │ ← preview at current size
│  └────────────────────────┘  │
│  Clock Size: 48sp       [───]│ ← slider 24-96
│  Clock Format               │
│  [ 24h ]  [ 12h ]           │ ← filter chips
│  "Drag clock to reposition"  │ ← helper text
│  Show Seconds           [○]  │ ← toggle
│  Snap to Grid           [●]  │ ← toggle
│                              │
│  ALBUMS                      │
│  ┌──────────────────────┐    │
│  │ 📷 Change Selection  │    │ ← button → album picker
│  └──────────────────────┘    │
│                              │
│  CONNECTION                  │
│  ┌──────────────────────┐    │
│  │ Server URL   [Edit]  │    │
│  │ API Key      [Edit]  │    │
│  │ [Reveal] [Copy]      │    │
│  │ [Test Connection]    │    │
│  └──────────────────────┘    │
│                              │
│  API KEY PERMISSIONS [Re-✓]  │ ← permission status card
│  ┌──────────────────────┐    │
│  │ ✓ user.read          │    │
│  │ ✓ album.read         │    │
│  │ ✓ asset.read         │    │
│  │ ✓ asset.view         │    │
│  │ ✗ asset.download     │    │ ← red if denied; card turns red if blocking missing
│  └──────────────────────┘    │
│                              │
│  Reset All Settings          │ ← red text button (preserves tour progress)
│                              │
│  Show Tour Again             │ ← replays Settings tour
│  Reset All Tours             │ ← replays all screens' tours
│                              │
└──────────────────────────────┘
```

- Organized into sections: Slideshow, Image, Media Cache, Night Mode, Clock, Albums, Connection, Permissions.
- Changes saved immediately to DataStore (no save button needed).
- "Test Connection" works same as setup screen.
- **API Key Permissions card** (below Connection): shows ✓/✗/? for each of the 5
  required permissions. Auto-refreshes when Settings opens; "Re-check" button
  re-probes. Card background turns error-colored if blocking permissions are
  missing. When `asset.download` is denied, the Skip Videos toggle in the
  Slideshow section is locked ON with an explanatory subtitle.
- Back arrow returns to previous screen.
- **System section** includes a version string in the top bar (`Settings vX.Y.Z`)
  and two tour replay buttons: **"Show Tour Again"** (replays only the Settings
  tour) and **"Reset All Tours"** (replays tours on all screens).

### Onboarding Tour Overlay

When the user enters a screen with uncompleted tour steps, a coachmark overlay
appears:

- Semi-transparent black scrim (78% alpha) covers the full screen.
- A rounded-rect spotlight cutout reveals the target element (button, field,
  or section header). A subtle white ring outlines the spotlight.
- A Material 3 `Card` tooltip appears below (or above, if space is tight) the
  spotlight, containing:
  - Step counter ("Step X of Y") in primary color
  - Close (X) icon to skip remaining steps
  - Step title (`titleMedium`) and body text (`bodyMedium`)
  - **Skip tour** text button + **Next** / **Got it** button
- Centered steps (no target) show the tooltip centered on screen (both
  horizontally and vertically) with a plain scrim (no cutout).
- In the Slideshow, the top control bar respects `statusBarsPadding()` and the
  bottom controls (progress bar, play/pause row) respect `navigationBarsPadding()`,
  so they never sit under the system status/navigation bars in any mode.
- In the Slideshow, controls are force-shown during the tour and the 5-second
  auto-hide is suppressed.
- In Settings, the tour scrolls each target section into view before showing
  its spotlight.
- Editing server URL or API key does not auto-navigate; the change takes
  effect next time the app fetches data (or when user manually restarts
  the slideshow).
- API key **Edit** empties the field for security (no pre-population);
  **Reveal** and **Copy** buttons appear when the key is set and require
  biometric / device-PIN authentication.
- Changing selected albums navigates back to the album picker.
- Auto-Update toggle is hidden when installed from Play Store
  (`getInstallSourceInfo() == "com.android.vending"`).
- Start on Boot shows a "Grant Display Over Other Apps" button when enabled
  if the `SYSTEM_ALERT_WINDOW` permission is not yet granted (required on
  Android 10+ for the boot receiver to launch the app — Background Activity
  Launch exemption).
- Start on Boot also shows an "Open Autostart Settings" button when enabled,
  which deep-links to the OEM-specific autostart permission screen
  (Xiaomi, Oppo, Vivo, Huawei, Honor, Asus, etc.).
- Launcher Mode, when enabled, shows an "Open Launcher Settings" button that
  opens the system Home settings page (`ACTION_HOME_SETTINGS`), allowing
  the user to switch to another launcher or re-select this app. The same
  action is available in the slideshow hover UI (apps icon in the top bar)
  when launcher mode is active.
- If the app loses its default-launcher status while Launcher Mode is
  enabled, a dialog appears on resume prompting the user to re-select
  Immich Media Frame as the default Home.
- Auto-Update, when visible, shows a "Check Now" button below it that
  triggers an immediate update check regardless of the toggle state. While
  active, the button label reflects state: "Checking for updates…",
  "Downloading update…", or "Update check failed" (error). The button is
  disabled while checking/downloading to prevent concurrent checks.

Material 3 dynamic colors are NOT used (frame context needs consistent dark background).

- Background: `#000000` (pure black, OLED-friendly)
- Surface: `#1A1A1A`
- Primary: `#6750A4` (Material 3 default purple) or user-configurable
- On controls overlay: semi-transparent black `#80000000`
- Text: white / off-white

## Accessibility

- Tap targets minimum 48dp.
- Settings controls labeled for TalkBack.
- High contrast between text and background.
- Album cards have text labels (not images-only).
