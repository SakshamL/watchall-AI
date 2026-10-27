# WatchAll — HLS Proxy Server

This Express server sits between your React frontend and Viduki's CDN.

## How it works

```
User clicks "Watch Now"
       │
       ▼
Frontend → GET /api/extract?tmdb=<id>
       │
       ▼
Server launches headless Chromium (Puppeteer)
Loads https://www.viduki.net/1/movie/<id>
Waits and intercepts the first .m3u8 network request
Returns the raw stream URL to the frontend
       │
       ▼
Frontend builds a proxied URL:
  /api/proxy?url=<encoded-m3u8-url>
       │
       ▼
HLS.js fetches the playlist through our proxy
Server rewrites all segment URLs inside the playlist
  to also go through /api/proxy
       │
       ▼
Every .ts segment request goes through the server
with the correct Referer: https://www.viduki.net/
       │
       ▼
Pure HTML5 <video> plays the stream — no ads, no branding
```

## Running

From the project root:
```bash
npm run dev:all   # starts Vite (port 5173) + Express server (port 3001)
```

Or separately:
```bash
npm run dev          # Vite frontend only
npm run dev:server   # Express backend only
```

## Endpoints

### `GET /api/extract`
Launches Puppeteer, loads the Viduki page, intercepts the `.m3u8` URL.

| Param | Description |
|-------|-------------|
| `tmdb` | TMDB movie/show ID |
| `type` | `movie` (default) or `tv` |
| `season` | Season number (TV only) |
| `episode` | Episode number (TV only) |
| `srv` | Optional server name (e.g. `Jill`, `Chris`). Forces the player to load that Viduki server by rewriting its `?srv=` API requests via CDP. Returns 404 if the server has no stream for the title. |

Returns: `{ "streamUrl": "https://...", "type": "hls" | "dash" | "mp4", "server": "Leon" }`

Results are cached in memory for 3 minutes (keyed by title/episode/server) so
repeat switches are instant.

### `GET /api/proxy`
Proxies any URL through the server, adding the correct `Referer` header.
For `.m3u8` playlists, all internal URLs are rewritten to go through this proxy too.

| Param | Description |
|-------|-------------|
| `url` | URL-encoded target URL |

## Notes on buffering & link expiry

- The `.m3u8` link from Viduki's CDN typically has a limited TTL (time-to-live).
- HLS.js naturally handles this: the stream is chunked, so it fetches each ~6 second
  segment separately. As long as playback is active, it keeps fetching new chunks.
- If the master `.m3u8` URL expires mid-playback, HLS.js will show an error.
  In that case, the frontend shows a "Retry" button that re-extracts a fresh URL.
- For stable, long-running sessions, consider re-extracting the URL every ~30 minutes
  in the background (future improvement).
