# Visual Guide

Screenshot reference for ImmichFrame. Each screen is shown with its states, and
**every setting is paired with the visual outcome it produces** in the running
slideshow (where applicable).

All screenshots are Full HD (1080×1920) generated from Compose `@Preview`
functions via Roborazzi — no device or emulator required. Source previews live
alongside each screen's composable.

---

## Table of Contents

1. [Setup](#1-setup)
2. [Album Selection](#2-album-selection)
3. [Slideshow States](#3-slideshow-states)
4. [Settings → Outcome Reference](#4-settings--outcome-reference)
   - [Playback](#41-playback)
   - [Photo Animations](#42-photo-animations)
   - [Display](#43-display)
   - [Night Mode](#44-night-mode)
   - [Clock](#45-clock)
   - [System](#46-system)
   - [Media Cache](#47-media-cache)
   - [Connection](#48-connection)
5. [Media Selection](#5-media-selection)

---

## 1. Setup

The first-run flow: domain validation → authentication → success.

| State | Screenshot |
|---|---|
| Empty (initial) | ![Setup — empty](screenshots/setup/domain_empty.png) |
| Domain filled | ![Setup — domain filled](screenshots/setup/domain_filled.png) |
| Connecting (validating server) | ![Setup — connecting](screenshots/setup/domain_connecting.png) |
| Domain error | ![Setup — domain error](screenshots/setup/domain_error.png) |
| Auth: Generate Key | ![Setup — generate key](screenshots/setup/auth_generate_key.png) |
| Auth: Enter Manually | ![Setup — manual key](screenshots/setup/auth_manual_key.png) |
| Auth: OAuth | ![Setup — OAuth](screenshots/setup/auth_oauth.png) |
| Auth success | ![Setup — success](screenshots/setup/auth_success.png) |

---

## 2. Album Selection

| State | Screenshot |
|---|---|
| Loading | ![Albums — loading](screenshots/albums/albums_loading.png) |
| Loaded | ![Albums — loaded](screenshots/albums/albums_loaded.png) |
| Error (server unreachable) | ![Albums — error](screenshots/albums/albums_error.png) |
| No albums available | ![Albums — no albums](screenshots/albums/albums_no_albums.png) |

---

## 3. Slideshow States

These represent the slideshow screen in various runtime states, independent of
individual settings (which are covered in [Section 4](#4-settings--outcome-reference)).

| State | Screenshot | Notes |
|---|---|---|
| Loading | ![Slideshow — loading](screenshots/slideshow/loading.png) | Initial asset fetch / cache warm-up |
| Controls visible | ![Slideshow — controls](screenshots/slideshow/controls_visible.png) | Tap to reveal top/bottom bars; auto-hide after 4 s |
| Paused | ![Slideshow — paused](screenshots/slideshow/paused.png) | Play/pause button in bottom bar; auto-advance frozen |

---

## 4. Settings → Outcome Reference

The core of this guide: each Settings section screenshot paired with the
slideshow screenshot(s) that show what the setting actually *does*.

Settings are organized into 8 sections. Below, each section lists its toggles
and — where the setting produces a visible change in the slideshow — links to
the screenshot that demonstrates the outcome.

---

### 4.1 Playback

**Settings panel:**

![Settings — Playback](screenshots/settings/playback.png)

| Setting | Default | Outcome |
|---|---|---|
| **Slideshow Interval** (5–120 s) | 30 s | Controls how long each photo stays before auto-advancing. Visible as the progress bar in the slideshow bottom bar (see [Controls visible](#3-slideshow-states)). |
| **Shuffle** | On | Randomizes photo order. Off = sequential (album order). No visible UI difference — affects playback sequence only. |
| **Skip Videos** | On | Only photos are shown; video assets are filtered out. Locked ON if the API key lacks the `asset.download` permission. |
| **Muted** | On | Silences video audio during playback. |

---

### 4.2 Photo Animations

**Settings panel** (shown with animations enabled and a mix of directions selected):

![Settings — Photo Animations](screenshots/settings/photo_animations.png)

| Setting | Default | Outcome |
|---|---|---|
| **Photo Animations** (master toggle) | Off | When ON, applies a subtle Ken Burns zoom/pan to each photo. Also serves as OLED burn-in protection for always-on displays. |
| ↳ **Zoom In** | On | Photo gradually zooms in over the display interval. |
| ↳ **Zoom Out** | On | Photo gradually zooms out. |
| ↳ **Pan Left** | On | Photo drifts leftward. |
| ↳ **Pan Right** | On | Photo drifts rightward. |
| ↳ **Pan Up** | On | Photo drifts upward. |
| ↳ **Pan Down** | On | Photo drifts downward. |

> **Note:** The animation is a continuous motion, so it can't be captured in a
> single static screenshot. The preview above shows the expanded sub-toggles
> that appear when the master toggle is ON.

---

### 4.3 Display

**Settings panel:**

![Settings — Display](screenshots/settings/display.png)

| Setting | Default | Outcome |
|---|---|---|
| **Image Fit: Contain** | Contain | Letterboxes the photo (black bars) to show the full image without cropping. |
| **Image Fit: Cover** | — | Crops the photo to fill the screen edge-to-edge (no bars). |
| **Adaptive Background** | Off | Fills the letterbox bars with a gradient sampled from the photo's edge colors instead of black. Only visible in Contain mode. |
| **Fullscreen** | On | Hides Android system bars (status + navigation). Off = bars always visible. |
| **Keep Screen On** | On | Acquires a wake lock so the screen never sleeps. |

**Outcome — Image Fit:**

| Contain (default) | Cover |
|---|---|
| ![Contain](screenshots/slideshow/photo_contain.png) | ![Cover](screenshots/slideshow/photo_cover.png) |

---

### 4.4 Night Mode

**Settings panel** (shown with Night Mode enabled, 22:00–07:00, 5% brightness):

![Settings — Night Mode](screenshots/settings/night_mode.png)

| Setting | Default | Outcome |
|---|---|---|
| **Night Mode** | Off | When ON, dims the screen during configured night hours. |
| **Dim screen at** | 22:00 | When the clock crosses this time, brightness drops to the night level. |
| **Brighten screen at** | 07:00 | When the clock crosses this time, brightness restores to the system level. |
| **Night brightness** (0–100%) | 0% | Screen brightness during night hours. 0% = near-black on OLED. |

**Outcome — Night Mode active:**

During the night window, the slideshow is hidden behind a black overlay and the
auto-advance timer is paused:

![Slideshow — night mode active](screenshots/slideshow/nightmode.png)

> The in-app Night Mode is a fallback for devices without native scheduled
> power on/off. The helper text in settings recommends the device's built-in
> schedule if available.

---

### 4.5 Clock

**Settings panel** (shown with clock enabled, 24 h, seconds on):

![Settings — Clock](screenshots/settings/clock.png)

| Setting | Default | Outcome |
|---|---|---|
| **Show Clock** | Off | Displays a time overlay on the slideshow. |
| **Clock Size** (24–96 sp) | 48 | Font size of the clock text. |
| **Clock Format** | 24 h | 12 h (with AM/PM) or 24 h. |
| **Show Seconds** | Off | Adds a seconds counter; updates every second. |
| **Snap to Grid** | On | Aligns the clock to a grid when released after dragging. |

**Outcome — Clock shown on slideshow:**

The clock is draggable — long-press and drag to reposition. With Snap to Grid
on, it aligns to the nearest grid point on release:

![Slideshow — with clock](screenshots/slideshow/with_clock.png)

---

### 4.6 System

**Settings panel** (shown with Start on Boot and Launcher Mode both ON):

![Settings — System](screenshots/settings/system.png)

| Setting | Default | Outcome |
|---|---|---|
| **Start on Boot** | Off | Launches the app automatically when the device boots. Requires the "Display over other apps" permission (Android 10+ BAL exemption). On restricted Chinese OEMs, an "Open Autostart Settings" button appears until a reboot confirms the receiver fired. |
| **Launcher Mode** *(visible only when Start on Boot is ON)* | Off | Registers the app as a Home launcher. The most reliable autostart: the system always launches the default Home on boot, bypassing `BOOT_COMPLETED` and OEM autostart blocks entirely. |
| **Auto-Update** | On | Checks GitHub for new builds on app start. Hidden if installed from the Play Store. |
| **Check Now** (button) | — | Triggers an immediate update check regardless of the Auto-Update toggle. |

> These settings have no in-slideshow visual outcome — they control device
> boot behavior and update checking, not the displayed photo.

---

### 4.7 Media Cache

**Settings panel:**

![Settings — Media Cache](screenshots/settings/media_cache.png)

| Setting | Default | Outcome |
|---|---|---|
| **Auto Sync** | On | Downloads new photos and removes deleted ones in the background via WorkManager. Enables offline viewing — when the server is unreachable, the slideshow shows cached photos instead of a black screen. |
| **Sync Interval** (1 min, or 5–480 min in steps of 5) | 30 min | How often the background sync checks for album changes. Clamped to 15 min minimum by WorkManager. |
| **Sync Now** (button) | — | Triggers an immediate one-time sync. |

> No in-slideshow visual difference — this controls background caching. The
> outcome is that photos continue to display when the server is offline.

---

### 4.8 Connection

**Settings panel** (shown with a configured server, masked API key, and all
permissions granted):

![Settings — Connection](screenshots/settings/connection.png)

| Element | Description |
|---|---|
| **Server URL** | Editable inline. The Immich server address. |
| **API Key** | Shown masked (`••••`). Tapping **Edit** empties the field (never pre-populated). When set, biometric-gated **Reveal** and **Copy** buttons appear. |
| **API Key Permissions** card | Lists all 5 required permissions with ✓ (granted), ✗ (denied), or ? (unknown). **Re-check** button re-probes all endpoints. Uses an error-colored background when a blocking permission is missing. |

**Permission gating:**

| Permission | Scope | Blocking? | Effect when missing |
|---|---|---|---|
| User read | `user.read` | Yes | Setup blocked. |
| Album read | `album.read` | Yes | Setup blocked. |
| Asset read | `asset.read` | Yes | Setup blocked. |
| Asset view | `asset.view` | Yes | Setup blocked. |
| Asset download | `asset.download` | No (optional) | **Skip Videos** toggle locked ON; media cache skips downloading originals. |

> No in-slideshow visual outcome — this section manages server credentials and
> permission status. Its effect is that the slideshow can connect and display
> photos at all.

---

## 5. Media Selection

Accessible from the slideshow top bar (grid icon). Biometric-gated.

| State | Screenshot | Notes |
|---|---|---|
| Loading | ![Media selection — loading](screenshots/media_selection/loading.png) | Fetching asset list (cache-first) |
| All shown | ![Media selection — all shown](screenshots/media_selection/all_shown.png) | Default mode: all photos visible, tap to hide |
| Some hidden | ![Media selection — some hidden](screenshots/media_selection/some_hidden.png) | Dimmed thumbnails = hidden from slideshow |

**"Show new photos by default" switch** controls the mode:
- **ON** (default): all media starts shown; new photos appear automatically.
- **OFF**: all media starts hidden; new photos stay hidden until manually shown.

Flipping the switch preserves the current visible selection — it only changes
the default for future new media.
