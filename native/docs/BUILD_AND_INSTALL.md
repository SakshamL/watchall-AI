# Build and install WatchAll Native

## 1. Prerequisites

- Android Studio with SDK Platform 37 and Build Tools 36/37.
- JDK 17. The commands below use Android Studio's bundled JDK.
- Node.js for the existing WatchAll backend.
- A phone running Android 8.0 (API 26) or newer, or an Android TV / Google TV device.
- ADB for installation.

The project uses Gradle 9.6.0, Android Gradle Plugin 9.5.0, Kotlin through AGP's built-in support,
Compose BOM 2026.09.00 and Media3 1.11.1.

## 2. Configure the native project

Create or edit `native/local.properties`:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
TMDB_API_KEY=<your TMDB v3 API key>
DEFAULT_API_BASE_URL=http://192.168.1.4:3001
```

Notes:

- `local.properties` is gitignored. Do not commit it.
- `DEFAULT_API_BASE_URL` is compiled into the shared core. The fallback used by the code when the
  property is absent is `http://10.0.2.2:3001`, which is the Android emulator's alias for the host
  machine.
- The checked-in project currently defaults to the LAN address `192.168.1.4:3001`. This is a DHCP
  address and can change; check it with `ipconfig` and rebuild, or change it in the app's Settings
  screen.
- The backend address can also be changed at runtime in the app's Settings screen.

## 3. Start the backend

From the repository root:

```bash
npm run dev:server
```

The server listens on port 3001. Verify it before installing the apps:

```bash
curl http://127.0.0.1:3001/api/servers
curl http://192.168.1.4:3001/api/servers
```

The second command checks the address a phone or TV will use. A successful response contains a
JSON `servers` array.

## 4. Build

From the `native` folder on Windows:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew.bat :mobile:assembleDebug :tv:assembleDebug
```

PowerShell equivalent:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :mobile:assembleDebug :tv:assembleDebug
```

Useful verification tasks:

```bash
./gradlew.bat :core:compileDebugKotlin :mobile:compileDebugKotlin :tv:compileDebugKotlin
./gradlew.bat :mobile:lintDebug :tv:lintDebug
```

Build outputs:

```text
native/mobile/build/outputs/apk/debug/mobile-debug.apk
native/tv/build/outputs/apk/debug/tv-debug.apk
```

Debug application IDs:

```text
com.watchall.mobile.debug
com.watchall.tv.debug
```

The `.debug` suffix allows the debug apps to be installed next to a future release build.

## 5. Install on a phone

Enable Developer Options and USB debugging, connect by USB, then:

```bash
adb devices
adb install -r native/mobile/build/outputs/apk/debug/mobile-debug.apk
```

If more than one device is connected:

```bash
adb -s <device-serial> install -r native/mobile/build/outputs/apk/debug/mobile-debug.apk
```

Launch the app from the launcher. The phone player switches to landscape automatically and restores
the previous orientation when you leave it.

## 6. Install on Android TV

Enable developer options on the TV, enable network debugging, then connect:

```bash
adb connect <TV-IP>:5555
adb devices
adb install -r native/tv/build/outputs/apk/debug/tv-debug.apk
```

Start it with either the launcher or:

```bash
adb shell am start -n com.watchall.tv.debug/com.watchall.tv.MainActivity
```

If installation fails with a version or signature conflict, uninstall the old copy first:

```bash
adb uninstall com.watchall.tv.debug
```

The TV manifest includes both the Leanback launcher category and a normal launcher category, so the
APK can be launched on a TV, a TV emulator, or a normal Android device for layout checks.

The TV interface is designed for about 960 x 540 dp, which is the space Android TV reports at 1080p.
A 4K television scales the same layout, so nothing needs to be rebuilt for 4K. If content looks
cropped at the edges, check the television's overscan or display-size setting.

## 7. First run

1. Open Settings and confirm the backend address.
2. Press Test connection and check that the server list is returned.
3. Return Home. The catalogue rows load from TMDB.
4. Open a title, press Play, and wait for stream extraction.
5. Open the source panel and confirm Server List 1 is selected.
6. For a movie, try Server List 2 from the source panel or from a failure message.

If TMDB fails, check the API key and internet access. If extraction fails, check the backend terminal
and confirm the backend can open the provider pages from that computer.

## 8. Rebuilding after code changes

The backend address and TMDB key are compiled into the app, so changing `local.properties` requires a
rebuild. Code-only changes can use incremental builds.

A clean build, if needed:

```bash
./gradlew.bat clean :mobile:assembleDebug :tv:assembleDebug
```

## 9. Release builds

The release build types have minification and resource shrinking enabled, but there is no release
signing configuration and no release APK in `native/docs`. Before shipping:

1. Add a private signing keystore through environment variables or a local Gradle properties file.
2. Review `mobile/proguard-rules.pro` and `tv/proguard-rules.pro` so Gson models and network response
   classes are not renamed.
3. Test a signed release build on real phone and TV hardware.
4. Remove the debug suffix and bump `versionCode` and `versionName`.
5. Treat the TMDB key and backend address as build-time configuration.

## 10. Common problems

| Problem | Check |
| --- | --- |
| App says the server cannot be reached | Backend running, correct LAN address in Settings, phone and computer on the same network, firewall allows port 3001 |
| TMDB catalogue is empty | `TMDB_API_KEY` is present in `local.properties`, the project was rebuilt, and the device has internet access |
| Server List 2 is slow or unavailable | It is a movie-only fallback and can take about 30 seconds; the backend may fail for titles that list does not carry |
| Player stays on "Finding your stream" | Backend terminal logs, provider availability, network access from the backend computer |
| TV app cannot be installed | Enable developer/network debugging, confirm `adb devices`, check the TV allows sideloading |
| No video but controls are visible | The backend extraction succeeded but the CDN playlist may have expired; press Try again or switch server |
