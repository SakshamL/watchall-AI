# WatchAll Native architecture

## 1. Module layout

```text
native/
  core/      shared Kotlin library: TMDB, backend API, storage, view models, Media3 player
  mobile/    phone application: Compose UI, tabs, details, player
  tv/        Android TV application: Compose UI, top navigation bar, D-pad focus, player
  docs/      this documentation
```

Packages:

```text
com.watchall.core
com.watchall.mobile
com.watchall.tv
```

The two apps never share Activity code. They share the `core` module, which keeps catalogue
parsing, resume logic and playback behavior identical.

## 2. Shared core

### `WatchAllGraph`

Created once by each app's `Application` class. It holds:

- `PreferencesStore`
- `WatchAllApi`
- `TmdbClient` inside `WatchAllRepository`
- `WatchAllRepository`

View models receive the graph or repository through constructors. There is no service locator
beyond the application graph.

### Networking

`TmdbClient`

- Reads the TMDB API key from `BuildConfig`.
- Loads home rows, catalogue pages, genres, search results, details and seasons.
- Caches small responses in memory with short time-to-live values.
- Ranks search results so exact title matches appear first.
- Uses parallel TMDB search endpoints for better mixed movie/TV results.
- Converts TMDB DTOs into app models.

`WatchAllApi`

- Talks to the WatchAll backend.
- Normalizes the backend base URL.
- Calls `/api/servers`, `/api/extract` and `/api/media-info`.
- Converts every playable URL to a `/api/proxy` URL before handing it to Media3, except when the
  backend already returned a proxied URL.
- Infers the stream type from the response, the URL extension or the media-info content type.
- Converts HTTP failures into user-safe messages.

`Http.kt`

- OkHttp with separate client policies for TMDB and the backend.
- Cancellation cancels the underlying OkHttp call, which matters when switching sources quickly.

### Storage

`PreferencesStore` uses private `SharedPreferences` under the name `watchall_native`.

Stored data:

- Backend base URL.
- Up to 30 resume entries, newest first.
- Watchlist.
- Up to 8 recent searches.

Resume entries are removed when they are under 10 seconds or at least 96% complete. Every write is
serialized with a lock so rapid progress saves cannot interleave badly.

### Playback

`PlaybackController` owns one ExoPlayer instance per player screen.

- `player` is exposed as the Media3 `Player` interface; the concrete player stays private.
- HLS, DASH and progressive MP4 are supported.
- The initial video cap is 1080p to avoid unnecessary load on low-power devices.
- Video quality, audio tracks, text tracks and playback speed are exposed through app models.
- A snapshot flow updates position, buffering, duration, tracks and failures twice per second.
- The controller is created when the player screen enters composition and released when it leaves.

### View models

- `HomeViewModel` — home rows and continue watching.
- `CatalogViewModel` — browse/catalogue filters, paging and genres.
- `SearchViewModel` — debounced search, filters and recent searches.
- `DetailsViewModel` — details, recommended titles, seasons, episodes and watchlist state.
- `PlayerViewModel` — servers, extraction, source switching, retry rules, resume saving and errors.
- `SettingsViewModel` — backend address and connection test.

Network jobs that can be replaced by a newer request rethrow `CancellationException` instead of
treating cancellation as a user-facing error. This prevents stale searches, stale catalogue pages
or stale extractions from overwriting newer state.

## 3. Server-list rules

The two visible choices are always **Server List 1** and **Server List 2**. Internal provider names
are never shown in the UI.

| Rule | Behavior |
| --- | --- |
| Default | Server List 1 |
| Server List 1 content | Movies and TV |
| Server List 2 content | Movies only |
| Server List 2 speed | Can take up to about 30 seconds |
| Automatic fallback | One next server inside Server List 1 only |
| Cross-list switching | Only after an explicit user action |
| Server names | List 1 server names may be shown |
| Errors | Use neutral list names and friendly messages |

`PlayerViewModel` extracts a stream, saves the current position before a source switch, and restarts
the new stream near that position. When automatic fallback happens, it sets a notice and tries one
next server in the same list. It never silently walks through many servers and never crosses to
Server List 2 on its own.

Failure hierarchy:

1. Extraction failure: primary action is to try the other list; secondary is to retry the current
   list. If both lists have failed, primary becomes retry and secondary becomes retry the other list.
2. Playback failure: primary is retry; secondary offers the other list.
3. Both lists failed: the message also explains that both lists were tried.
4. The source panel remains available for a specific server or list choice.

## 4. Resume behavior

`ResumeEntry` stores:

- media key, title and subtitle
- poster and backdrop paths
- media type and numeric media ID
- original language
- season, episode and episode title
- position, duration and update time

The player saves progress every five seconds and when it is closed. It also saves on disposal. The
Home screen and Library read the same entries. Old entries without a numeric ID can recover the ID
from the media key.

## 5. Phone UI

Root: `WatchAllMobileApp.kt`.

- Four bottom tabs: Home, Search, Library, Settings.
- Overlay stack for Details and Player.
- Back removes the top overlay, then returns to Home if the user is on another tab.
- Each player session gets a unique ViewModel key so playback state cannot leak between titles.
- The player tab is hidden while a full-screen overlay is open.

Screens use a dark cinema palette, cyan accents, rounded surfaces and compact bottom navigation.
Lazy lists are used where appropriate. The Home hero uses a horizontal pager.

The phone player:

- Forces landscape while visible and restores the previous orientation afterward.
- Hides system bars and restores them on exit.
- Uses Media3 `PlayerView` with custom Compose controls.
- Provides seek, play/pause, skip 10 seconds, source, quality, audio, subtitles and speed.
- Saves progress when leaving and releases the player immediately.

## 6. TV UI

Root: `TvApp.kt`.

- Fixed 58 dp top navigation bar: Home, Browse, Search, Library, Settings.
- The selected tab is tinted cyan; the bar also shows the brand and a small "private, ad-free" label.
- Overlay stack for Details, Collections, cast filmographies and Player.
- Back handling mirrors the phone.
- Home refreshes continue watching when a player closes.
- Each tab keeps its scroll position and state through a `SaveableStateHolder`, so switching tabs
  never reloads the screen.

The TV layout is sized for a 10-foot screen. Android TV reports roughly 960 x 540 dp at 1080p, so the
design uses a 120 dp poster, a 320 dp hero and 40 dp page padding. Those numbers fit six full posters in a
row, plus a peek of the next, while leaving the first row visible under the hero.

Media rows use `LazyRow` and grids use `LazyVerticalGrid`, so only the visible cards are composed. The
pressed card is brought into view with `BringIntoViewRequester`.

`TvFocusable` provides:

- a 110 ms scale animation to 1.05
- a static 2 dp cyan border when focused
- optional focus requesters, including `left` and `down` routing
- optional bring-into-view behavior
- click and focus semantics

Focus routing is explicit at the root. Pressing Down on the navigation bar jumps to the selected
screen's primary focus target through `LocalTvContentFocusRequester`. On launch, on tab change and
after an overlay closes, focus returns to the selected nav item.

TV screens request an initial meaningful focus target after loading:

- Home: hero play button, or the first row when there is no hero
- Browse: Movies filter
- Search: search field
- Collection: first poster
- Details: play button after the first episode is available, once per visit
- Library: first resume card, the first saved poster, or the empty-state action
- Person: first poster after the filmography loads, or the retry action on failure
- Player: play button when controls appear, error action on failure, root surface when controls hide

Loading states use a branded mark (two rotating arcs around a pulsing gradient play tile) instead of a
plain spinner. Home shows a skeleton feed with one shared pulse animation rather than dozens of
independent shimmers.

The TV player handles the remote explicitly:

- Media play/pause, fast-forward and rewind keys.
- D-pad or Enter first reveals hidden controls.
- Back exits the player or closes a dialog.
- The root surface becomes focusable when controls are hidden, so the remote keeps working.
- Focus moves through explicit zones: top bar, centre transport, seek bar and bottom action row.
  Closing a sheet returns focus to the control that opened it.
- Sheets use technology-neutral list names, focus the first useful option and include a visible Close
  button as well as Back handling.

The TV play action opens a server chooser before playback. The chooser lists every source list,
disables the movie-only list for TV shows, focuses the first available option and returns focus to the
play button when dismissed. Details also scrolls back to the hero when focus returns to that button
from a row further down the page.

## 7. Visual system

Both apps use a dark ink background, cyan/teal accents, restrained violet highlights and light text.
The shared look is implemented separately in `mobile/ui/theme` and `tv/ui/theme` so each form factor can
choose its own scale and spacing. TV type is larger than phone type because of viewing distance, but it
stays compact enough to fit a full row of content on a 540 dp-high screen.

## 8. Configuration

The core module compiles:

- `TMDB_API_KEY`
- `DEFAULT_API_BASE_URL`

from `native/local.properties` into `BuildConfig`. The settings screen overrides only the backend
URL; the API key cannot be changed at runtime.

Cleartext HTTP is allowed in both network security configs because the backend is normally a LAN
development server. Playback itself always goes through the backend proxy.

## 9. Implementation sequence

1. Create the Gradle project and shared core.
2. Implement TMDB, backend, preferences, repository and playback.
3. Build the phone app: navigation, catalogue, details, player, settings.
4. Build the TV app: top navigation, focus system, catalogue, details, player.
5. Wire TV root navigation and player lifecycle.
6. Fix compile, lint and platform issues.
7. Harden async behavior: cancellation, focus stealing, hidden-player back handling.
8. Verify backend extraction and proxy behavior from the development machine.
9. Build final debug APKs and document verification limits.

## 10. Boundaries and future work

- Only the `native/` tree and the repository implementation log were changed. The web app was left
  untouched.
- Playback depends on the existing backend extractor and proxy. Changes to provider pages or CDN
  behavior can break titles independently of the native apps.
- Runtime verification on real phone and TV hardware is still required.
- Release signing, release minification validation and store packaging are not included.
