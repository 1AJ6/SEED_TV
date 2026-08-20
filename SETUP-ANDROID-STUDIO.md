# S.E.E.D TV Mobile — Android Studio Setup

Welcome to the dev seat, Ethan. This gets you from zip → app running
on your phone with live UI editing.

## Requirements

- **Android Studio Meerkat (2024.3.1) or newer** — AGP 8.9 needs it.
  (Help → About to check. If older, update via the in-app updater.)
- Your phone + USB cable (or Wi-Fi pairing).

## 1. Open the project

1. Unzip `seedtv-mobile-source.zip` somewhere permanent
   (e.g. `C:\dev\seedtv-mobile` — avoid OneDrive/Dropbox folders).
2. Android Studio → **Open** → select the `seedtv-mobile` folder
   (the one containing `settings.gradle.kts`).
3. First sync takes a few minutes: Studio auto-downloads the Android SDK 36,
   build-tools and all libraries. Accept any SDK license prompts.
4. Done when the Build pane says "Sync finished" with no red errors.

## 2. Run it on your phone

1. On the phone: Settings → About phone → tap **Build number** 7× →
   back → **Developer options** → enable **USB debugging**.
2. Plug in the phone, accept the "Allow USB debugging?" prompt.
3. Your device appears in the toolbar dropdown → press **Run ▶**.

Because `signing/seedtv-debug.keystore` is committed, Studio builds carry the
SAME signature as the alpha APKs — you can install over any existing alpha
without uninstalling. (`versionCode` lives in `app/build.gradle.kts`; bump it
if the phone refuses to replace a newer build.)

## 3. Live UI editing (the "visual GUI" workflow)

- **Compose Preview**: open any `*Screen.kt` → click the split-view icon
  (top-right) → previews render beside the code without a device.
- **Live Edit** (the magic): Settings → Editor → Live Edit → enable
  "Push Edits Automatically". Run the app on your phone, then edit any
  Compose code — sizes, colors, paddings, text — and watch the phone update
  **as you type**, no rebuild.
- Start with these files for the things you've been tuning:
  | What | File |
  |---|---|
  | Top bar size/text | `core/designsystem/.../CompactTopBar.kt` |
  | Bottom nav bar | `app/.../navigation/SeedTvNavHost.kt` |
  | Colors/theme | `core/designsystem/.../Theme.kt` |
  | Player controls | `feature/player/.../PlayerScreen.kt` |
  | Waiting room / chat | `feature/syncplay/.../SyncWaitScreen.kt` |
  | Settings screen | `app/.../navigation/SettingsScreen.kt` |

## 4. Useful commands (Studio terminal)

```
gradlew :app:assembleDebug     # debug APK → app/build/outputs/apk/debug
gradlew test                   # unit tests
gradlew :app:assembleRelease   # (M6) release build
```

## 5. Recommended next step: version control

```
git init && git add . && git commit -m "S.E.E.D TV Mobile 0.1.0-alpha24"
```
Push to a private GitHub/GitLab repo so we both have history from here on.

## Project map

```
app/                 entry point, navigation, settings screen, crash reporter
core/common          shared types, dispatchers, scrobble contract
core/designsystem    theme + CompactTopBar
core/database        Room (mappings, sync queue, prefs)
core/jellyfin        SDK wrapper: auth, library, playback info, sessions
core/playback        libVLC engine, playback controller, SyncPlay coordinator,
                     LAN chat (mDNS + sockets)
core/anilist         GraphQL client, rate limiter, sync queue processor
core/matching        anime detection + AniList resolution pipeline
feature/*            onboarding, library, player, syncplay, anilist screens
scripts/             headless build pipeline (used by the AI-sandbox side)
signing/             shared debug keystore (debug builds only!)
```

Design doc: ask in chat for `seedtv-design-doc.md` — architecture §7 covers
the (now bypassed) SyncPlay protocol; the native replacement lives in
`SyncPlayCoordinator.kt` with full comments.
