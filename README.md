# SEED TV

Created and owned by **Ethan Sayer**.

**SEED TV** is a high-performance, native Android client for **Jellyfin** (10.11+) designed for enthusiasts who demand a premium playback experience. By combining the power of the **libVLC** engine with modern Android architecture, SEED TV delivers "Jellyfin, the VLC way."

---

## The Experience

SEED TV isn't just another media browser; it's a specialized tool built for smooth playback, community watching, and deep anime integration.

### 🎬 Professional Playback
*   **VLC-Class Engine:** Powered by libVLC 3.7.5 for superior codec support and "Direct Play" by default.
*   **Gesture Mastery:** Intuitive vertical swipes for volume (left) and brightness (right) with real-time level bars and percentage readouts.
*   **Advanced Controls:** Native support for Picture-in-Picture (PiP), A-B repeat loops, playback speed adjustment, and per-show audio/subtitle delay offsets.
*   **Episode Navigation:** Dedicated Skip Next/Previous buttons and an automated "Next Episode" countdown card.

### 👥 Native SyncPlay
*   **Real-time Synchronization:** Watch movies and shows together with friends. SEED TV keeps everyone's play/pause state and seek position in perfect sync.
*   **Waiting Room:** A dedicated lobby where members can chat and see a live thumbnail of what the host is about to start.
*   **Integrated Chat:** A lightweight, transparent chat overlay that lets you talk to your group without obscuring the media.

### 🌸 Anime Enthusiast Features
*   **AniList Auto-Sync:** Link your AniList account to automatically track your progress.
*   **Smart Matching:** An advanced detection pipeline that resolves Jellyfin series and seasons to their corresponding AniList entries.
*   **Sync Queue:** A durable background system ensures your scrobbles are uploaded even if your connection is spotty.

### 🎨 Personalized for You
*   **Theme Presets:** Choose from over 10 custom color schemes including *Ember*, *Midnight*, *Cyberpunk*, and *OLED*.
*   **Layout Control:** Tailor your home screen experience with *Grid*, *List*, or *Compact* viewing modes.
*   **Clean Identity:** Pure, dark neutral backgrounds designed to make your media posters pop and look incredible on OLED displays.

---

## Technical Architecture

Built from the ground up using **Jetpack Compose** and **Kotlin**, SEED TV follows a clean, multi-module MVVM structure. 

*   **App:** Entry point, navigation graph, and global dependency injection (Hilt).
*   **Core:** Shared logic for database management (Room), networking (OkHttp), and the VLC/Jellyfin SDK wrappers.
*   **Features:** Isolated modules for onboarding, library browsing, player UI, and SyncPlay coordination.

---

## Credits & Acknowledgments

SEED TV is built on the shoulders of these incredible open-source projects:

- **[libVLC](https://www.videolan.org/vlc/libvlc.html)** by **VideoLAN** — The core playback engine.
- **[Jellyfin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin)** by the **Jellyfin Project** — Native API integration.
- **[Kotlin](https://kotlinlang.org/)** by **JetBrains** — The primary programming language.
- **[Jetpack Compose](https://developer.android.com/compose)** and **Android Jetpack** by **Google** — Modern UI and architecture components.
- **[Hilt](https://dagger.dev/hilt/)** by **Google** — Dependency injection.
- **[Coil](https://coil-kt.github.io/coil/)** by **Coil Contributors** — Image loading.
- **[OkHttp](https://square.github.io/okhttp/)** by **Square, Inc.** — Networking.
- **[Turbine](https://github.com/cashapp/turbine)** by **Cash App** — Testing library for Kotlin Flows.

---

## License

SEED TV is licensed under the **GPLv3**. See [LICENSE](LICENSE) for details.
