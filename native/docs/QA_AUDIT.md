# WatchAll Native QA audit

Audit date: 2026-11-01
Last build: 2026-11-02
Scope: `native/` phone and TV Android apps
Result: code compiles, both lint tasks pass with 0 errors, both debug APKs build, backend extraction
and proxy paths were checked from the development machine. Runtime UI/playback verification on real
phone or TV hardware is still outstanding.

## 1. Environment

- Windows 11 host.
- Android SDK Platform 37, build tools 36/37.
- JDK from Android Studio 2026.2.2.
- Only emulator available: `Medium_Phone_API_37.0`.
- No physical phone, Android TV or Google TV device was connected.
- Backend on `192.168.1.4:3001` was reachable during the API checks.
- ADB path: `C:\Users\saksh\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## 2. Build verification

Commands:

```bash
cd native
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew.bat :core:compileDebugKotlin :mobile:compileDebugKotlin :tv:compileDebugKotlin
./gradlew.bat :mobile:lintDebug :tv:lintDebug
./gradlew.bat :mobile:assembleDebug :tv:assembleDebug
```

Results:

| Check | Result |
| --- | --- |
| Core Kotlin compile | Pass |
| Phone Kotlin compile | Pass, no warnings after final cleanup |
| TV Kotlin compile | Pass, no warnings after final cleanup |
| Phone lint | Pass, 0 errors, 7 warnings |
| TV lint | Pass, 0 errors, 7 warnings |
| Phone debug APK | Built successfully |
| TV debug APK | Built successfully |
| Unit tests | None defined in the project |
| Instrumented tests | None defined in the project |

APK outputs:

```text
native/mobile/build/outputs/apk/debug/mobile-debug.apk   24,627,401 bytes
native/tv/build/outputs/apk/debug/tv-debug.apk           24,241,956 bytes
```

SHA-256:

```text
b7deb24f3e095ce66a7df07d7bb90e8108008f8aaf868d84465f6cab4e8ec09e  mobile-debug.apk
d3f102c03dcf7a1ef836f3d0a4fb04eefb3ed1f21e040308ece385dd2655b343  tv-debug.apk
```

## 3. Lint warnings

Each app reports the same seven warnings:

1. Four launcher icon shape suggestions. The supplied raster icons fill their square area. They work,
   but adaptive foreground/background icons would remove the warning and look better on modern
   launchers.
2. `DiscouragedApi` for fixed screen orientation. The phone player requests landscape and the TV app
   is landscape. Android 16 may ignore fixed orientation on large screens. The manifest already
   handles configuration changes, so the layout should still rotate correctly, but this needs device
   testing.
3. `InsecureBaseConfiguration` because cleartext HTTP is allowed. This is intentional for a LAN
   backend. It should be narrowed to known hosts or replaced with HTTPS for wider deployment.
4. `DataExtractionRules` because `android:allowBackup="false"` is deprecated on Android 12+. Backups
   are disabled, so user data is not currently at risk, but a modern data extraction rules resource
   would remove the warning.

No lint errors remain. Earlier Media3 unstable-API errors are handled with a local
`@SuppressLint("UnsafeOptInUsageError")` on the two player screens.

## 4. Backend and stream verification

These checks used the backend API directly from the host, not the Android apps.

| Check | Title / ID | Result |
| --- | --- | --- |
| Backend reachable | `/api/servers` | HTTP 200 with server array |
| Server List 1 movie extraction | Inception, TMDB 27205 | HLS, server Leon |
| Proxy playback URL | Inception HLS | HTTP 200, m3u8 content type, playlist bytes returned |
| Playlist rewriting | Inception HLS | Segment URLs point back through `/api/proxy` |
| Server List 1 TV extraction | Breaking Bad S1E1, TMDB 1396 | HLS, server Leon |
| TV proxy | Breaking Bad S1E1 | HTTP 200, m3u8 content type, playlist bytes returned |
| Server List 2 movie extraction | The Dark Knight, TMDB 155 | HLS, server Vidnest (internal name) |
| Server List 2 proxy | The Dark Knight | HTTP 200, m3u8 content type, playlist bytes returned |

One older movie, Inception, returned HTTP 502 from Server List 2 while it played from Server List 1.
This matches the expected fallback model: Server List 2 does not carry every title. The apps show a
friendly error and offer Server List 1.

Playback through ExoPlayer itself was not executed because no Android runtime was available.

## 5. Code-review checklist

### Data and networking

- [x] TMDB key read from build configuration, never hardcoded in source.
- [x] Backend URL normalized and editable at runtime.
- [x] Stream URLs routed through `/api/proxy`.
- [x] Relative backend URLs resolved.
- [x] HLS, DASH and MP4 type handling.
- [x] HTTP errors converted to user-facing text.
- [x] Internal provider names removed from visible strings.
- [x] Parallel search requests rethrow cancellation.
- [x] Cancelled catalogue, search, season and extraction jobs no longer overwrite newer state.

### Storage

- [x] Resume, watchlist, recent searches and settings persisted privately.
- [x] Resume entries capped and expired near completion.
- [x] Watchlist and resume keyed by media type plus ID.
- [x] Settings default path works when the stored address is removed.

### Phone app

- [x] Home, Search, Library, Settings, Details and Player wired.
- [x] Home rows use nine curated queries with friendly language names and Top 10 ranking.
- [x] Every Home category row has a View More action opening a paged poster grid.
- [x] Collection screen pages through TMDB results with Load More, retry and error states.
- [x] Detail-to-player and resume-to-player paths include the correct movie or episode.
- [x] Back stack handles Details and Player overlays.
- [x] Player forces landscape and restores orientation.
- [x] Player saves progress on exit and every five seconds.
- [x] Player releases Media3 resources on disposal.
- [x] Hidden-player Back handling disabled while a sheet is visible.
- [x] Footer refresh action is clickable.

### TV app

- [x] Fixed 58 dp top navigation bar with five destinations.
- [x] Down on the nav bar jumps to the selected screen's primary focus target.
- [x] Focus returns to the selected nav item on launch, tab change and overlay close.
- [x] All cards, chips, rows, actions and list items are focusable.
- [x] Initial focus stays on the navigation bar at launch; each screen exposes its primary target for Down.
- [x] Details, Collection, player and error states request their own focus target when they open.
- [x] Details no longer steals focus when changing seasons or episodes.
- [x] TV Details uses a cinematic hero, overlaid metadata and actions, horizontal episode cards, cast and recommendations.
- [x] Section headings use a 22 sp bold title with a cyan accent bar; page titles use 30 sp.
- [x] Primary buttons use a cyan/teal gradient with dark text; secondary buttons use dark card fills. Both use 9 dp corners and clear focused states.
- [x] Cast cards are focusable and open a filmography page with All, Movies and TV filters.
- [x] The main TV play action opens a server chooser; Server List 2 is shown disabled for TV shows.
- [x] Cancelling the server chooser returns focus to the play button.
- [x] Returning focus to the Details play button from a lower row scrolls the page back to the top.
- [x] TV Home uses an immersive hero, curated rows, Top 10 ranking and View More per category.
- [x] TV screens use a compact 10-foot scale: 120 dp posters, 320 dp hero and 40 dp page padding.
- [x] TV media rows use `LazyRow` and grids use `LazyVerticalGrid`, so off-screen cards are not composed.
- [x] TV tab scroll position and state survive tab switches through a `SaveableStateHolder`.
- [x] Horizontal card rows auto-scroll, and focused rows scroll vertically into view.
- [x] Collection grid opens from View More and focuses the first poster.
- [x] Library focuses the saved grid when continue watching is empty.
- [x] Focused items brought into view.
- [x] Focus styling uses a 110 ms scale and a static 2 dp border (cyan, or white on primary buttons) with no animated shadow.
- [x] Player and first-load states use the branded loader instead of a plain spinner.
- [x] Player remote keys limited to media and navigation keys.
- [x] Player root surface focusable when controls are hidden.
- [x] Player seek bar is focusable; Left/Right seek by 10 seconds and OK toggles playback.
- [x] Player controls auto-hide 6.5 seconds after the latest remote interaction, including while the seek bar has focus.
- [x] Seeking keeps controls visible until the seek stops, then the normal hide timer restarts.
- [x] Player Back closes a dialog first, then exits.
- [x] Source, quality, audio, subtitle and speed panels use a shared focusable list pattern.
- [x] Player focus moves through explicit zones: top bar, centre transport, seek bar and bottom action row.
- [x] Closing a player sheet returns focus to the control that opened it, and each sheet has a visible Close button.
- [x] Detail screen uses the full available height.

### Error and source behavior

- [x] Default list is Server List 1.
- [x] Server List 2 excluded from TV playback: disabled in the pre-play chooser and hidden in the player source sheet.
- [x] One automatic fallback only inside Server List 1.
- [x] No automatic switch to Server List 2.
- [x] Position preserved when switching source or server.
- [x] Extraction error offers the other list first.
- [x] Playback error keeps retry first.
- [x] Both-lists failure note shown.
- [x] Specific server selection available from the source panel.

### Security and configuration

- [x] `local.properties` gitignored.
- [x] No secret committed to source or docs.
- [x] Debug builds do not use release keys.
- [x] Cleartext traffic limited by documented intent, not accidental.
- [x] No exported receivers or services beyond the launcher activities.

## 6. Runtime testing status

### Not completed

- App launch on a real phone.
- App launch on a real Android TV or Google TV.
- D-pad traversal on TV hardware.
- Real video, audio, subtitle and quality switching through the app.
- Automatic fallback after a real playback error.
- Resume after process death and app restart.
- Settings connection test from a device.
- Orientation restore on a phone.
- Back behavior on TV while a dialog or keyboard is open.
- TV controls auto-hiding after seeking on real hardware.
- Focus path from Home row action to View More and back.
- TV Details hero and episode focus traversal on a real TV.
- Cast card to filmography page traversal and back, including filter changes.
- Server chooser focus, disabled Server List 2 for TV shows, and focus return on cancel.
- Curated Home row content and Top 10 ranking correctness in the device UI.
- Down-from-nav routing and return-to-nav focus on a real remote.
- TV layout at 960 x 540 dp: whether poster rows, headings and the hero fit without clipping.
- Branded loader and skeleton appearance, and whether the skeleton pulse is smooth on low-end TV hardware.
- Mobile tab crossfade and overlay transitions on a real phone.

### Why

The available API 37 emulator never became visible to ADB. `emulator.exe` was started, but
`adb devices` stayed empty and `adb wait-for-device` timed out. The emulator process was no longer
running afterward. The likely cause is unavailable hardware acceleration for the installed system
image. No physical phone, TV or TV emulator was connected.

A runtime check is still required before calling the apps production-ready.

## 7. Recommended next tests

1. Install the phone APK on a real Android 8+ phone connected by USB.
2. Confirm Home loads, search returns results, details opens, and a movie starts.
3. Confirm landscape entry and restore; lock/unlock and background/foreground during playback.
4. Confirm resume appears and returns to the same position and episode.
5. Force a source failure by choosing a server that is unlikely to work, then check the other-list
   action and both-lists state.
6. Install the TV APK on an Android TV emulator or device.
7. Walk every screen with only the D-pad. Check the top nav bar, Home hero, rows, Browse filters,
   Search keyboard, Library cards, settings fields, details actions, episode row and player panels.
8. Check that focus is always visible, never disappears after loading, and does not jump when a
   season changes.
9. Verify the TV player source panel is reachable while controls are visible and hidden.
10. Test the release build and minification before distribution.

## 8. Release readiness

Not release-ready yet.

Blocking items:

- No signed release build.
- No real-device phone or TV playback test.
- TV focus and remote behavior need hardware verification.

Non-blocking polish:

- Replace raster launcher icons with adaptive icons.
- Add Android 12+ data extraction rules.
- Narrow the cleartext network configuration for production.
- Add automated view-model and mapping tests.
