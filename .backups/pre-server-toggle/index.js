/**
 * WatchAll — HLS Proxy Server
 *
 * Optimizations over the basic version:
 * 1. Keep-alive HTTP connections — reuse the TCP connection to the CDN
 *    instead of doing a fresh handshake for every .ts segment.
 * 2. In-memory segment cache — once a segment is fetched, it lives in
 *    memory. Re-requests (seeks, retries) are served instantly.
 * 3. Proactive prefetching — when we send a playlist back to HLS.js,
 *    we immediately start fetching the next 5 segments into cache so
 *    they're ready before the browser even asks for them.
 */

const express = require("express");
const cors = require("cors");
const axios = require("axios");
const http = require("http");
const https = require("https");
const { URL } = require("url");

// puppeteer-extra + stealth plugin bypasses all major headless detection vectors
const puppeteer = require("puppeteer-extra");
const StealthPlugin = require("puppeteer-extra-plugin-stealth");
puppeteer.use(StealthPlugin());

const app = express();
const PORT = 3001;

app.use(cors());

// ─── Keep-alive HTTP agents ───────────────────────────────────────────────────
// Reusing the TCP connection to the CDN saves ~50-150ms per segment
// by skipping the TCP + TLS handshake that normally happens every request.

const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 20 });
const httpsAgent = new https.Agent({ keepAlive: true, maxSockets: 20 });

// Shared headers to include in every CDN request
const CDN_HEADERS = {
  "User-Agent":
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36",
  Referer: "https://www.viduki.net/",
  Origin: "https://www.viduki.net",
};

// ─── In-memory segment cache ──────────────────────────────────────────────────
// Stores raw .ts segment buffers keyed by their CDN URL.
// Max 60 segments (~6 minutes of video at 6s/segment) before evicting oldest.

const MAX_CACHE = 60;
const segmentCache = new Map();

function cacheSet(url, buffer) {
  if (segmentCache.size >= MAX_CACHE) {
    // Evict the oldest entry (Map preserves insertion order)
    segmentCache.delete(segmentCache.keys().next().value);
  }
  segmentCache.set(url, buffer);
}

// ─── Proactive prefetch ───────────────────────────────────────────────────────
// Silently downloads a segment into cache. Errors are intentionally ignored
// since this is best-effort — HLS.js will fetch normally if prefetch missed.

async function prefetch(url) {
  if (segmentCache.has(url)) return; // already warm
  try {
    const res = await axios({
      method: "GET",
      url,
      responseType: "arraybuffer",
      timeout: 15000,
      headers: CDN_HEADERS,
      httpAgent,
      httpsAgent,
    });
    cacheSet(url, Buffer.from(res.data));
    console.log(`[prefetch] cached ${url.split("/").pop()}`);
  } catch {
    // silent — prefetch is best-effort
  }
}

// ─── 1. EXTRACT — headless Puppeteer finds the stream URL ──────────────────────
//
// Viduki's API exposes the same title across several named servers:
//   srv=Leon / Jill / Ada / Claire  → HLS  (CDN returns .m3u8 playlists)
//   srv=Sherry                      → progressive MP4 (signed CDN URL, no .m3u8)
// For some titles every HLS server answers 404 and the player falls back to a
// direct MP4 — so detection must catch multiple formats, not just ".m3u8".

app.get("/api/extract", async (req, res) => {
  const { tmdb, type = "movie", season, episode } = req.query;

  if (!tmdb) {
    return res.status(400).json({ error: "Missing tmdb query param" });
  }

  const vidukiUrl =
    type === "tv" && season && episode
      ? `https://www.viduki.net/1/tv/${tmdb}/${season}/${episode}`
      : `https://www.viduki.net/1/movie/${tmdb}?color=e01621`;

  console.log(`[extract] Loading: ${vidukiUrl}`);

  let browser;
  try {
    browser = await puppeteer.launch({
      headless: "new", // "new" = Chrome Headless, closer to real browser than headless:true (shell mode)
      args: [
        "--no-sandbox",
        "--disable-setuid-sandbox",
        "--disable-gpu",
        "--disable-blink-features=AutomationControlled",
      ],
    });

    const page = await browser.newPage();
    await page.setViewport({ width: 1280, height: 720 });
    await page.setUserAgent(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    );

    const candidates = { hls: null, dash: null, mp4: null };

    // Bounded diagnostic log of everything the page fetched
    const requestLog = [];          // { method, url, type, status }
    const MAX_LOG = 600;
    let consoleErrors = 0;

    function rememberRequest(entry) {
      if (requestLog.length < MAX_LOG) requestLog.push(entry);
    }

    function noteCandidate(kind, url) {
      if (candidates[kind]) return;
      candidates[kind] = url;
      console.log(`[extract] Detected ${kind.toUpperCase()} stream: ${url.slice(0, 180)}`);
    }

    // Simple request listener — no interception (interception conflicts with stealth plugin)
    page.on("request", (request) => {
      const url = request.url();
      const resourceType = request.resourceType();
      rememberRequest({ method: request.method(), url, type: resourceType });

      // Extension-based detection
      if (/\.m3u8([?#]|$)/i.test(url)) {
        noteCandidate("hls", url);
      } else if (/\.mpd([?#]|$)/i.test(url)) {
        noteCandidate("dash", url);
      } else if (/\.mp4([?#]|$)/i.test(url) && (resourceType === "media" || resourceType === "navigate")) {
        // Signed progressive MP4 (e.g. macdn.hakunaymatata.com/resource/….mp4?Expires=…)
        noteCandidate("mp4", url);
      }
    });

    // Content-type based detection — catches extension-less HLS/DASH manifests
    page.on("response", (response) => {
      try {
        const url = response.url();
        let status = null;
        let ct = "";
        try {
          status = response.status();
          ct = (response.headers()["content-type"] || "").toLowerCase();
        } catch {
          // response may already be gone off the event bus — ignore
        }
        const entry = requestLog.find((r) => r.url === url && r.status === undefined);
        if (entry) entry.status = status;
        else rememberRequest({ method: "GET", url, type: "response", status });

        if (ct.includes("mpegurl")) noteCandidate("hls", url);
        else if (ct.includes("dash+xml")) noteCandidate("dash", url);
        else if (ct.startsWith("video/") && /\.mp4([?#]|$)/i.test(url)) noteCandidate("mp4", url);
      } catch {
        // diagnostics only — never break extraction
      }
    });

    page.on("requestfailed", (request) => {
      const url = request.url();
      rememberRequest({ method: request.method(), url, type: "failed", error: request.failure()?.errorText });
    });

    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors++;
    });

    // networkidle2 waits for the network to quiet down — this catches most cases.
    // Some movies take longer for the player to init and fire the stream request,
    // so we poll for up to 15 more seconds rather than a fixed sleep.
    await page.goto(vidukiUrl, { waitUntil: "networkidle2", timeout: 30000 });

    const deadline = Date.now() + 15000;
    let mp4GraceEnd = null;
    while (Date.now() < deadline) {
      if (candidates.hls) break; // HLS is what the UI prefers — return ASAP
      if (candidates.dash) break; // proper streaming format — return ASAP

      if (candidates.mp4) {
        // MP4-only title: keep listening briefly in case a better format shows up,
        // then settle for the MP4 instead of failing.
        if (mp4GraceEnd === null) {
          mp4GraceEnd = Date.now() + 5000;
        } else if (Date.now() > mp4GraceEnd) {
          break;
        }
      }
      await new Promise((r) => setTimeout(r, 500));
    }

    const streamUrl = candidates.hls || candidates.dash || candidates.mp4;
    const streamType = candidates.hls ? "hls" : candidates.dash ? "dash" : "mp4";

    if (!streamUrl) {
      const diagnostics = buildDiagnostics(requestLog, consoleErrors);
      console.error("[extract] No stream found. Diagnostics:");
      console.error(`  console errors: ${diagnostics.consoleErrors}`);
      console.error(`  requests captured: ${diagnostics.requestsCaptured}, hosts: ${diagnostics.hosts.join(", ")}`);
      diagnostics.serverApiCalls.forEach((c) => console.error(`  [api] ${c.status} ${c.url}`));
      diagnostics.mediaUrls.forEach((c) => console.error(`  [media] ${c}`));
      console.error("  last requests:");
      diagnostics.recentRequests.forEach((c) => console.error(`    ${c}`));
      console.error(`  page HTML still contains player? ${diagnostics.playerPresent}`);

      return res.status(404).json({
        error: "No stream URL found. The movie may not be available.",
        diagnostics,
      });
    }

    res.json({ streamUrl, type: streamType });

  } catch (err) {
    console.error("[extract] Error:", err.message);
    res.status(500).json({ error: "Extraction failed", details: err.message });
  } finally {
    if (browser) await browser.close();
  }
});

/**
 * Summarises what the page actually loaded when no stream was detected.
 * In practice the key line is the list of `api.viduki.net/main/…` calls: when
 * every srv=… answers 404, the title has no playable source on Viduki.
 */
function buildDiagnostics(requestLog, consoleErrors) {
  const statusOf = (url) => {
    const hit = requestLog.find((r) => r.url === url && r.status);
    return hit ? hit.status : "?";
  };

  const serverApiCalls = requestLog
    .filter((r) => r.type === "fetch" && r.url.includes("api.viduki.net/main/"))
    .map((r) => ({ url: r.url, status: statusOf(r.url) }));

  const mediaUrls = [
    ...new Set(
      requestLog
        .filter((r) => /\.(m3u8|mpd|mp4|ts|m4s|vtt|key)([?#]|$)/i.test(r.url))
        .map((r) => `${r.method} ${r.type} ${r.url}`)
    ),
  ].slice(0, 15);

  const hosts = [
    ...new Set(requestLog.map((r) => { try { return new URL(r.url).host; } catch { return "(bad-url)"; } })),
  ];

  const recentRequests = requestLog.slice(-20).map((r) => `${r.method} ${r.type} ${r.url}`);

  return {
    requestsCaptured: requestLog.length,
    consoleErrors,
    hosts: hosts.slice(0, 20),
    serverApiCalls,
    mediaUrls,
    recentRequests,
    playerPresent: mediaUrls.length > 0 || serverApiCalls.length > 0,
  };
}


// ─── 2. PROXY — serve segments with cache + keep-alive ───────────────────────

app.get("/api/proxy", async (req, res) => {
  const targetUrl = req.query.url;

  if (!targetUrl) {
    return res.status(400).json({ error: "Missing url query param" });
  }

  res.setHeader("Access-Control-Allow-Origin", "*");

  // ── Cache hit: serve the segment from memory instantly ──
  if (segmentCache.has(targetUrl)) {
    console.log(`[cache hit] ${targetUrl.split("/").pop()}`);
    const buf = segmentCache.get(targetUrl);
    res.setHeader("Content-Type", "video/mp2t");
    return res.send(buf);
  }

  const isM3u8 = targetUrl.includes(".m3u8");

  try {
    if (isM3u8) {
      // ── Playlist: fetch as text, rewrite URLs, prefetch first segments ──
      const response = await axios({
        method: "GET",
        url: targetUrl,
        responseType: "text",
        timeout: 15000,
        headers: CDN_HEADERS,
        httpAgent,
        httpsAgent,
      });

      const baseUrl = new URL(targetUrl);
      const { rewritten, segmentUrls } = rewritePlaylist(response.data, baseUrl);

      res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
      res.send(rewritten);

      // Proactively prefetch the first 5 segments in the background
      // so they're in cache before HLS.js requests them
      segmentUrls.slice(0, 5).forEach((url) => prefetch(url));

    } else {
      // ── Segment: fetch with keep-alive, cache it, then pipe to client ──
      const response = await axios({
        method: "GET",
        url: targetUrl,
        responseType: "arraybuffer",
        timeout: 15000,
        headers: CDN_HEADERS,
        httpAgent,
        httpsAgent,
      });

      const buffer = Buffer.from(response.data);
      cacheSet(targetUrl, buffer); // store for future seeks

      const contentType =
        response.headers["content-type"] || "video/mp2t";
      res.setHeader("Content-Type", contentType);
      res.send(buffer);
    }
  } catch (err) {
    console.error("[proxy] Error:", err.message);
    res.status(500).json({ error: "Proxy failed", details: err.message });
  }
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Rewrites every segment/sub-playlist URL in the m3u8 to go through
 * our /api/proxy. Also returns the list of absolute segment URLs so
 * we can prefetch them immediately.
 */
function rewritePlaylist(text, baseUrl) {
  const segmentUrls = [];

  const rewritten = text
    .split("\n")
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) return line;

      let absolute;
      try {
        absolute = new URL(trimmed, baseUrl).toString();
      } catch {
        return line;
      }

      // Collect segment URLs for prefetching (skip sub-playlists)
      if (!absolute.includes(".m3u8")) {
        segmentUrls.push(absolute);
      }

      return `/api/proxy?url=${encodeURIComponent(absolute)}`;
    })
    .join("\n");

  return { rewritten, segmentUrls };
}

// ─── Start ────────────────────────────────────────────────────────────────────

app.listen(PORT, () => {
  console.log(`✅ WatchAll proxy server running at http://localhost:${PORT}`);
  console.log(`   Segment cache: up to ${MAX_CACHE} segments in memory`);
  console.log(`   Keep-alive CDN connections: enabled`);
  console.log(`   Proactive prefetch: 5 segments ahead`);
});
