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
│                              │
│                              │
│                              │
│                              │
└──────────────────────────────┘
```

Fullscreen, immersive mode (status bar + nav bar hidden).

**On tap** — overlay controls fade in:

```
┌──────────────────────────────┐
│ [Album Name]           [⚙] [✕]│
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
│  SERVER                      │
│  Immich Server URL           │
│  ┌────────────────────────┐  │
│  │ https://photos.dav3.cc │  │
│  └────────────────────────┘  │
│  API Key                     │
│  ┌────────────────────────┐  │
│  │ ••••••••••             │  │
│  └────────────────────────┘  │
│  ┌──────────────────────┐    │
│  │   Test Connection    │    │
│  └──────────────────────┘    │
│                              │
│  SLIDESHOW                   │
│  Interval: 30s          [───]│ ← slider 5-300s
│  Transition: 1s         [───]│ ← slider 0-3s
│  Fill Mode: Contain     [▾]  │ ← dropdown
│  Ken Burns Effect       [○]  │ ← toggle
│  Show Clock             [○]  │ ← toggle
│  Keep Screen On         [●]  │ ← toggle
│                              │
└──────────────────────────────┘
```

- Organized into sections: Server, Slideshow.
- Changes saved immediately to DataStore (no save button needed).
- "Test Connection" works same as setup screen.
- Back arrow returns to previous screen.
- Editing server URL or API key does not auto-navigate; the change takes
  effect next time the app fetches data (or when user manually restarts
  the slideshow).
- Changing selected albums navigates back to the album picker.

## Color Palette

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
