# WatchAll Native for Android phone and Android TV

This folder contains the native WatchAll apps. They are separate Android applications built with
Kotlin, Jetpack Compose and Media3. The existing React/Node web app was not replaced or modified.

## Deliverables

| App | Debug APK | Size | Application ID | Requirements |
| --- | --- | --- | --- | --- |
| Phone | `mobile/build/outputs/apk/debug/mobile-debug.apk` | 23.5 MB | `com.watchall.mobile.debug` | Android 8.0 (API 26) or newer |
| Android TV | `tv/build/outputs/apk/debug/tv-debug.apk` | 23.1 MB | `com.watchall.tv.debug` | Android TV / Google TV, Android 8.0 or newer |

SHA-256 at the time of the final build on 2026-11-02:

```
b7deb24f3e095ce66a7df07d7bb90e8108008f8aaf868d84465f6cab4e8ec09e  mobile-debug.apk
d3f102c03dcf7a1ef836f3d0a4fb04eefb3ed1f21e040308ece385dd2655b343  tv-debug.apk
```

These are debug APKs signed with the Android debug key. They are for testing and private
installation, not for public distribution. A release build needs its own signing key and a review of
the minification rules.

## What is included

- Shared core for TMDB catalogue data, WatchAll backend calls, preferences, resume history,
  watchlist and Media3 playback.
- Phone app with Home, Search, Library, Settings, Details and a landscape Player.
- Android TV app with a D-pad-first top navigation bar, Home, Browse, Search, Library, Settings,
  Details, cast filmographies, Collections, Player and remote-friendly source, quality, audio,
  subtitle and speed panels.
- Compact 10-foot TV layout: 120 dp posters, a 320 dp hero and 40 dp page padding, so a full row of
  titles is visible on a 1080p screen without oversized cards.
- Prominent 22 sp section headings with a cyan accent bar and 30 sp page titles, so section names are
  readable from couch distance.
- Cast cards on TV Details open a filmography page with All, Movies and TV filters.
- A server chooser appears before the main TV play action, matching the web player; Server List 2 is
  shown as disabled for TV shows because it is movies-only.
- Lazy TV rows and grids, a 110 ms focus animation and a static focus border so D-pad movement stays
  smooth on low-end boxes.
- Branded loading animation (rotating arcs around a gradient play tile) on the phone and TV, plus a
  pulsing Home skeleton instead of a blank screen on first load.
- Two server-list model with explicit switching. Server List 1 is recommended. Server List 2 is a
  movie-only fallback and may take up to about 30 seconds to start.
- Resume playback, watchlist, recent searches and editable backend address stored on the device.
- All playback goes through the WatchAll backend proxy so CDN headers and playlists are handled in
  one place.
- Mobile and TV launcher icons and a TV banner.

## Requirements

1. Android SDK 37 and the JDK bundled with Android Studio.
2. A running WatchAll backend on port 3001.
3. A watchable title available from the backend.
4. A TMDB API key in `native/local.properties` (this file is gitignored).
5. The phone or TV and the backend computer on the same network, unless the backend is exposed on a
   reachable address.

## Quick start

Start the backend from the repository root:

```bash
npm run dev:server
```

Confirm it answers:

```bash
curl http://192.168.1.4:3001/api/servers
```

Build the APKs:

```bash
cd native
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew.bat :mobile:assembleDebug :tv:assembleDebug
```

Install the phone app:

```bash
adb install -r native/mobile/build/outputs/apk/debug/mobile-debug.apk
```

Install the TV app on a TV or emulator connected with ADB:

```bash
adb connect <TV-IP>:5555
adb install -r native/tv/build/outputs/apk/debug/tv-debug.apk
adb shell am start -n com.watchall.tv.debug/com.watchall.tv.MainActivity
```

If the backend address is different, open Settings in either app and enter the new address, or
change `DEFAULT_API_BASE_URL` in `native/local.properties` and rebuild.

## Documentation index

- `BUILD_AND_INSTALL.md` — build, configuration and installation steps.
- `ARCHITECTURE.md` — code structure, data flow, playback rules and TV focus design.
- `QA_AUDIT.md` — what was tested, what passed, known warnings and what remains unverified.
- `IMPLEMENTATION_LOG.txt` — dated, plain-English record of the native app work.

## Security notes

- `native/local.properties` contains the TMDB API key and must never be committed or shared.
- The debug APKs contain that key in compiled form. Treat the APKs as private test builds.
- Cleartext HTTP is allowed so the apps can reach a LAN backend. Use HTTPS if the backend is ever
  exposed outside a trusted network.
- No release keystore is included.
