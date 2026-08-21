# <img src="app/src/main/res/mipmap-xxxhdpi/ic_logo.png" width="48" align="center" /> SEED TV

[![Build Status](https://img.shields.io/github/actions/workflow/status/1AJ6/SEED_TV/build-release.yml?branch=main)](https://github.com/1AJ6/SEED_TV/actions)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)

**SEED TV** is a high-performance, native Android client for **Jellyfin** (10.11+) designed for enthusiasts who demand a premium playback experience. By combining the power of the **libVLC** engine with modern Android architecture, SEED TV delivers "Jellyfin, the VLC way."

Created and owned by **Ethan Sayer**.

---

## 📱 Screenshots

| Home (Grid) | Home (List) | Player UI |
| :---: | :---: | :---: |
| ![Home Grid](https://via.placeholder.com/200x400?text=Home+Grid) | ![Home List](https://via.placeholder.com/200x400?text=Home+List) | ![Player](https://via.placeholder.com/200x400?text=Player+Controls) |

---

## ✨ Features

### 🎬 Professional Playback
*   **VLC-Class Engine:** Powered by libVLC 3.7.5 for superior codec support and "Direct Play" by default.
*   **Gesture Mastery:** Intuitive vertical swipes for volume (left) and brightness (right) with real-time level bars.
*   **Advanced Controls:** Native support for PiP, A-B repeat, playback speed, and audio/subtitle delays.
*   **Episode Navigation:** Dedicated skip buttons and automated "Next Episode" countdowns.

### 👥 Native SyncPlay
*   **Real-time Synchronization:** Watch together with friends with perfectly synced play/pause and seeks.
*   **Waiting Room:** A dedicated lobby where members can chat and preview upcoming media.

### 🌸 Anime Enthusiast Features
*   **AniList Auto-Sync:** Link your AniList account to automatically track your anime progress.
*   **Smart Matching:** Advanced detection pipeline resolves Jellyfin media to AniList entries.

### 🎨 Personalized for You
*   **Theme Presets:** Over 15 custom color schemes including *Ember*, *Midnight*, *Cyberpunk*, and *OLED*.
*   **Layout Control:** Tailor your experience with *Grid*, *List*, or *Compact* viewing modes.

---

## 🛠 Tech Stack

*   **UI:** [Jetpack Compose](https://developer.android.com/compose)
*   **Architecture:** MVVM + Clean Architecture (Multi-module)
*   **DI:** [Hilt](https://dagger.dev/hilt/)
*   **Local DB:** [Room](https://developer.android.com/training/data-storage/room)
*   **Playback:** [libVLC Android](https://www.videolan.org/vlc/libvlc.html)
*   **Image Loading:** [Coil 3](https://coil-kt.github.io/coil/)

---

## 🤝 Credits & Acknowledgments

SEED TV is built on the shoulders of these incredible open-source projects:

- **[libVLC](https://www.videolan.org/vlc/libvlc.html)** by **VideoLAN** — The core playback engine.
- **[Jellyfin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin)** by the **Jellyfin Project** — Native API integration.
- **[Kotlin](https://kotlinlang.org/)** by **JetBrains** — The primary programming language.
- **[Android Jetpack](https://developer.android.com/jetpack)** by **Google** — Modern UI and architecture components.
- **[Hilt](https://dagger.dev/hilt/)** by **Google** — Dependency injection.
- **[Coil](https://coil-kt.github.io/coil/)** by **Coil Contributors** — Image loading.
- **[OkHttp](https://square.github.io/okhttp/)** by **Square, Inc.** — Networking.
- **[Turbine](https://github.com/cashapp/turbine)** by **Cash App** — Testing library.

---

## 📄 License

Distributed under the **GNU General Public License v3.0**. See `LICENSE` for more information.

---
*Built with ❤️ by Ethan Sayer*
