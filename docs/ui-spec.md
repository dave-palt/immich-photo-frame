# UI Specification

Material 3 design. Dark theme by default (photo frame context — images
look better against dark background). Light theme available as a setting.

## Screens

### 1. Setup Screen

Shown on first launch or when credentials are missing/invalid.

```
┌──────────────────────────────┐
│                              │
│         [App Logo]           │
│                              │
│   Immich Server URL          │
│   ┌────────────────────────┐ │
│   │ https://...            │ │
│   └────────────────────────┘ │
│                              │
│   API Key                    │
│   ┌────────────────────────┐ │
│   │ ••••••••••••••••••     │ │
│   └────────────────────────┘ │
│                              │
│   ┌──────────────────────┐   │
│   │   Test Connection    │   │
│   └──────────────────────┘   │
│                              │
│   Status: [idle/connecting/  │
│            success/error]    │
│                              │
└──────────────────────────────┘
```

- URL field auto-normalizes: strips trailing slashes, auto-adds `http://`
  if no scheme present.
- API key field is masked (password input).
- "Test Connection" button validates URL format, then calls the API.
- Status area shows:
  - Idle: "Enter your Immich server details"
  - Connecting: spinner + "Connecting to {url}..."
  - Success: checkmark + "Connected as {user email}"
  - Error: red text with specific error (timeout, 401, DNS failure, etc.)
- On success, "Continue" button appears (or auto-navigates after 1s delay).

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
- **Update status icon** (left of Settings): spinner while checking,
  download icon while downloading, red on error, highlighted when ready to
  install. Tap when ready opens the install dialog.
- Controls are semi-transparent overlay, do not push image.
- Controls auto-hide after 5 seconds.
- Immersive flag re-engages when controls hide.

### 4. Settings Screen

Accessible from album selection (gear icon) or slideshow controls (gear icon).

```
┌──────────────────────────────┐
│  ← Settings                  │
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
│  "Drag clock to reposition"  │ ← helper text
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
│  │ [Test Connection]    │    │
│  └──────────────────────┘    │
│                              │
│  Reset All Settings          │ ← red text button
│                              │
└──────────────────────────────┘
```

- Organized into sections: Slideshow, Image, Media Cache, Clock, Albums, Connection.
- Changes saved immediately to DataStore (no save button needed).
- "Test Connection" works same as setup screen.
- Back arrow returns to previous screen.
- **System section** includes a "Show Tour Again" button that replays the
  onboarding tour across all screens.

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
- Centered steps (no target) show the tooltip centered on screen with a plain
  scrim (no cutout).
- In the Slideshow, controls are force-shown during the tour and the 5-second
  auto-hide is suppressed.
- In Settings, the tour scrolls each target section into view before showing
  its spotlight.
- Editing server URL or API key does not auto-navigate; the change takes
  effect next time the app fetches data (or when user manually restarts
  the slideshow).
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
