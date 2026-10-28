# WatchAll — Stream Extractor API

This Express app extracts Viduki stream URLs and serves the WatchAll API.

## How it works

```
Watch page opens
       │
       ▼
Frontend → GET /api/extract?tmdb=<id>
       │
       ▼
Server connects to a browser (Browserless in the cloud, local Chrome in dev)
Loads https://www.viduki.net/1/movie/<id>
Waits for the player to request a stream and checks that the CDN responds
Returns the raw stream URL to the frontend
       │
       ▼
HLS.js / DASH.js / native video plays the CDN URL directly
No video bytes pass through the server
```

Direct playback works because the CDN accepts requests from the WatchAll site
and sends `Access-Control-Allow-Origin: *`. Playing the raw stream also avoids
Viduki's embedded player, so its UI and ads are not shown.

The `/api/proxy` endpoint is still available as a fallback, but the player no
longer uses it by default.

## Running locally

From the project root:
```bash
npm run dev:all   # starts Vite (port 5173) + Express server (port 3001)
```

Or separately:
```bash
npm run dev          # Vite frontend only
npm run dev:server   # Express backend only
```

Local runs launch the installed Chrome. To use Browserless locally, set
`BROWSERLESS_TOKEN` in the shell before starting the server.

## Deployment (Vercel)

The frontend and API deploy together on Vercel:

- `api/index.js` exposes the Express app as a serverless function.
- `BROWSERLESS_TOKEN` connects the function to Browserless (free plan works).
- `BROWSERLESS_URL` is optional and defaults to
  `wss://production-lon.browserless.io/stealth`.
- `VITE_TMDB_API_KEY` and `VITE_TMDB_BASE_URL` are required for the frontend.

## Endpoints

### `GET /api/extract`
Loads the Viduki page in a browser and returns a working stream URL.

| Param | Description |
|-------|-------------|
| `tmdb` | TMDB movie/show ID |
| `type` | `movie` (default) or `tv` |
| `season` | Season number (TV only) |
| `episode` | Episode number (TV only) |
| `lang` | Original language from TMDB (`hi` tries Hindi servers first) |
| `srv` | Optional server name (e.g. `Jill`, `Chris`). Forces the player to load that Viduki server by rewriting its `?srv=` API requests via CDP. Returns 404 if the server has no stream for the title. |

Returns: `{ "streamUrl": "https://...", "type": "hls" | "dash" | "mp4", "server": "Leon" }`

Results are cached in memory for 3 minutes (keyed by title/episode/server) so
repeat switches are instant.

### `GET /api/servers`
Returns the server list used by the player's source picker. The list is
harvested passively during extraction; a built-in fallback list is returned on
a cold start so the picker is available immediately.

### `GET /api/media-info`
Returns the content type of a media URL. Used only when the stream type is
unknown.

### `GET /api/proxy`
Optional fallback proxy that adds the Viduki `Referer` header and rewrites
playlist URLs. Not used by the default direct-playback path.

| Param | Description |
|-------|-------------|
| `url` | URL-encoded target URL |

## Notes on link expiry

- Viduki's CDN URLs typically have a limited lifetime.
- Playback keeps working because HLS.js fetches each segment separately.
- If the master playlist expires mid-playback, the player shows an error and
  the Retry button extracts a fresh URL.
