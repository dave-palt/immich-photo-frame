## ImmichFrame v0.3.0

Native Android photo frame app for Immich.

### What's New since v0.2.0

- **Interactive onboarding tour** — coachmark overlays guide users through setup, album selection, slideshow controls (including back-to-albums and update indicator), and settings. Replayable per-screen or globally.
- **Biometric auth** — fingerprint / face / PIN protection for API key reveal & copy
- **Media selection grid** — tap to show/hide individual photos from the slideshow (biometric-gated)
- **Offline cache display fix** — cached files are now used for display when the server is unreachable; slideshow keeps playing from cache
- **Album deletion detection** — deleted albums (404) are detected; user is sent back to album selection
- **Self-update overhaul** — semver comparison for release builds, download progress UI with resume support, observability logging, hover-icon indicator
- **Launcher icon redesign** — day/night adaptive variants, Android 13+ monochrome (themed icon), dedicated debug variant, separate app logo for Setup screen
- **API key security** — edit empties the field (no pre-population), reveal & copy are biometric-gated

### Features

- Direct Immich API access (no middleman server)
- Native Jetpack Compose UI (no WebView)
- Album picker with multi-select
- Fullscreen slideshow with crossfade transitions
- Configurable interval, transition speed, fill mode (Contain/Cover)
- Video playback with mute and skip options (Media3/ExoPlayer)
- Draggable clock overlay with configurable size and snap-to-grid
- Photo animations (Ken Burns) — also serves as burn-in protection
- Adaptive background (fills letterbox bars with dominant color)
- Shuffle mode for randomized image order
- Progress bar showing time remaining per image
- Start on boot (SYSTEM_ALERT_WINDOW BAL exemption + OEM autostart detection)
- Launcher Mode (Home replacement) — most reliable boot method
- Self-update via GitHub releases with manual "Check Now"
- Offline media cache with background sync (Room + WorkManager)
- Auto-resumes last album on launch
- Interactive onboarding tour (replayable)
- Adaptive launcher icon (day/night + monochrome)
- Localized into 13 languages (en, ar, zh, nl, fr, de, it, ja, ko, pl, pt, ru, es)
- Scoped API key (4 permissions only)
- API key stored encrypted on-device (AES-256, Android Keystore)
- Biometric-protected API key reveal & copy
- Media selection grid — tap to show/hide individual photos
