# S.E.E.D TV Mobile

Created and owned by **Ethan Sayer**.

A native Android client for **Jellyfin** (10.11+) with a **VLC-class player** (libVLC),
**AniList auto-sync** for anime, and **native SyncPlay**.

Licensed under **GPLv3** — see [LICENSE](LICENSE).

## Status

**M2 — player core** — ✅ v0.1.0-alpha3 APK sideload-ready (`S.E.E.D TV-Mobile-0.1.0-alpha3-arm64-debug.apk`). Alpha1/2 install + login confirmed on device.

> **Low-memory build machines:** use `./scripts/build-apk-batched.sh` — it splits the build
> into 6 short stages with per-stage memory profiles (compile stages need metaspace,
> DEX stages need heap) so nothing OOMs or exceeds CI step timeouts.

| Milestone | Scope | Status |
|---|---|---|
| M0 | Modules, DI, theme, server add + auth (password & Quick Connect), session mgmt | ✅ done |
| M1 | Library browsing (Home, grids, detail, search) | ✅ done (builds + tests green) |
| M2 | Player core (libVLC playback, Direct Play, resume, progress reporting, VLC-style controls v1) | ✅ done (builds green) |
| M3 | VLC gesture parity, autoplay, PiP, A-B, delays | ✅ done |
| M4 | AniList (OAuth link, matching pipeline, scrobbler, sync queue, UI) | ✅ code complete (device testing) |
| M5 | SyncPlay (time sync, coordinator, drift correction, group UX) | ✅ code complete (device testing) |
| M6 | Hardening → **first alpha** | ⬜ |

Design changes are frozen until after first alpha testing (Ethan Sayer's decision, 2026-08-17).
See the full design document: `../seedtv-design-doc.md`.

## Architecture

Single-activity Jetpack Compose app, MVVM, multi-module:

```
app                    → DI graph, navigation, MainActivity
core/common            → SResult, dispatchers, app scope
core/designsystem      → theme (S.E.E.D TV Ember palette — NOT VLC branding)
core/database          → Room: servers, accounts, mappings, sync queue, history
core/jellyfin          → SDK wrapper, SessionManager, AuthRepository, SocketHub
core/playback          → PlayerEngine interface + LibVlcEngine (libVLC 3.7.x)
core/anilist           → rate limiter, ScrobbleTarget contract, GraphQL client (M4)
core/matching          → anime detection & AniList resolution pipeline (M4)
feature/onboarding     → server add, cleartext gate, login, Quick Connect
feature/library        → browsing (M1)
feature/player         → VLC-clone UI (M2/M3)
feature/syncplay       → group UX (M5)
feature/anilist        → account link, mappings review, sync history (M4)
```

**Golden rule:** no libVLC types outside `core/playback`; no Jellyfin SDK types
outside `core/jellyfin` mappers (enforced in review).

## Building

Requirements: JDK 17, AGP 8.9, Android SDK 36 (compileSdk 36 is forced by libVLC 3.7.5).

No Android Studio needed — headless build on any Linux x64 box:

```bash
./scripts/setup-buildenv.sh   # one-time: JDK 17 + Gradle 8.11.1 + Android SDK → ~/.cache/devtools
./scripts/build-apk.sh        # → app/build/outputs/apk/debug/app-debug.apk (arm64)
```

### Installing on your phone (sideload)

1. Go to the [Releases](https://github.com/your-username/seedtv-mobile/releases) page on GitHub.
2. Download the latest `app-debug.apk`.
3. Open it; allow **Install unknown apps** for your file manager/browser when prompted.
4. arm64 phones only for debug builds (99% of devices from ~2017+).

## Contributing

As an open-source project under the **GPLv3** license, contributions are welcome!

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

Please ensure your code follows the **Golden rule**: no libVLC types outside `core/playback`; no Jellyfin SDK types outside `core/jellyfin` mappers.

## Credits & Acknowledgments

S.E.E.D TV Mobile is built on the shoulders of these incredible open-source projects:

- **[libVLC](https://www.videolan.org/vlc/libvlc.html)** by **VideoLAN** — The core playback engine.
- **[Jellyfin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin)** by the **Jellyfin Project** — Native API integration.
- **[Kotlin](https://kotlinlang.org/)** by **JetBrains** — The primary programming language.
- **[Jetpack Compose](https://developer.android.com/compose)** and **Android Jetpack** by **Google** — Modern UI and architecture components.
- **[Hilt](https://dagger.dev/hilt/)** by **Google** — Dependency injection.
- **[Coil](https://coil-kt.github.io/coil/)** by **Coil Contributors** — Image loading.
- **[OkHttp](https://square.github.io/okhttp/)** by **Square, Inc.** — Networking.
- **[Turbine](https://github.com/cashapp/turbine)** by **Cash App** — Testing library for Kotlin Flows.

## Key product decisions (2026-08-17)

- License: **GPLv3**
- Min Jellyfin server: **10.11** (hard gate at login)
- Manual "mark watched" **does** scrobble to AniList (same guard-rail pipeline)
- VLC behavior cloned; VLC branding/trademarks **not** used
