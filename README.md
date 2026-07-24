# ImmichFrame — Native Android Photo Frame for Immich

A native Android slideshow app that connects directly to your Immich server.
No intermediary Docker container, no WebView, no second API key.

## Why?

[ImmichFrame_Android](https://github.com/immichFrame/ImmichFrame_Android) uses
a server+client architecture where a Docker container holds your Immich API
key and serves a web UI that the Android app loads in a WebView. This only
makes sense if every screen in your house shows the same content. For a
single photo frame, it's unnecessary complexity.

This app talks to the Immich API directly using `x-api-key` authentication,
lets you pick which album(s) to display, and remembers your choice.

## Features

- Direct Immich API access (no middleman server)
- Native Jetpack Compose UI (no WebView)
- Album picker with multi-select
- Fullscreen slideshow with crossfade transitions
- Configurable interval, transition speed, fill mode
- Ken Burns effect (optional)
- Auto-resumes last album on launch
- API key stored encrypted on-device

## Setup

1. Create an API key in Immich: User Settings > API Keys
2. Enter server URL + API key in the app
3. Select album(s)
4. Slideshow starts

## Tech Stack

Kotlin · Jetpack Compose · Retrofit · Coil 3 · Hilt · DataStore

## Requirements

- Immich server (v1.120+)
- Android 8.0+ (API 26)

## Documentation

- [Overview](docs/overview.md) — goals and architecture
- [Functional Spec](docs/functional-spec.md) — features and user flows
- [Technical Spec](docs/technical-spec.md) — tech stack and data flow
- [API Reference](docs/api-reference.md) — Immich API endpoints used
- [UI Spec](docs/ui-spec.md) — screen layouts

## License

MIT
