# Overview

## Problem

[ImmichFrame_Android](https://github.com/immichFrame/ImmichFrame_Android) uses an intermediary server architecture: a Docker container (ImmichFrame Server) holds your Immich API key, fetches images from Immich, and serves a web UI. The Android app is a WebView pointed at that container. An optional `AuthenticationSecret` gates access to the frame server.

This design only makes sense when every screen in a household shows the same content from a single shared config. If different devices need different albums or users, you need a separate container for each — defeating the purpose of centralization.

## Solution

A native Android app that connects directly to the Immich server using an API key. No intermediary server, no Docker, no WebView.

```
[Immich Server] ← x-api-key header → [App (native)]
```

The app stores the server URL and API key on-device, lets the user pick which album(s) to display, and runs a fullscreen slideshow. On subsequent launches it resumes the last-selected album automatically.

## Goals

- Direct Immich API access (no middleman server)
- Native Android UI (Jetpack Compose, no WebView)
- Album picker with persistent multi-select selection
- Fullscreen slideshow with crossfade transitions
- Video playback (optional, with mute/skip controls)
- Draggable clock overlay with configurable size and position
- Photo animations (Ken Burns zoom/pan) — also serves as burn-in protection for always-on displays
- Adaptive background (fills letterbox bars with each photo's dominant color)
- Start-on-boot for dedicated frame devices
- Self-update via GitHub releases (bypasses Play Store for sideloaded installs)
- Offline-capable media cache (Room + WorkManager background sync)
- Minimal setup: enter URL + API key, pick album, done
- Sensible defaults, configurable later

## Non-Goals

- Multi-user / multi-server support
- Weather, calendar, or metadata overlays
- Screensaver / DreamService integration
- Android TV / Leanback support
- Server-side image caching (all caching is local on-device)
- Play Store publishing (deferred — AAB is built and ready, but not auto-published)

These may be revisited in later versions.
