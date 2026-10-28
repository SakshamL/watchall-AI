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
const PORT = Number(process.env.PORT) || 3001;
const BUILD = "stream-probe-v6 " + new Date().toISOString();
const BROWSERLESS_URL =
  process.env.BROWSERLESS_URL || "wss://production-lon.browserless.io/stealth";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function browserlessEndpoint() {
  const token = String(process.env.BROWSERLESS_TOKEN || "").trim();
  if (!token) return "";
  const url = new URL(BROWSERLESS_URL);
  url.searchParams.set("token", token);
  url.searchParams.set("timeout", "110000");
  return url.toString();
}

function localLaunchOptions() {
  return {
    headless: "new",
    args: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-gpu",
      "--disable-blink-features=AutomationControlled",
    ],
  };
}

async function getBrowser() {
  const endpoint = browserlessEndpoint();
  if (endpoint) {
    console.log("[browser] Connecting to Browserless");
    try {
      return await puppeteer.connect({ browserWSEndpoint: endpoint });
    } catch (error) {
      console.error(`[browser] Browserless connection failed (${error?.name || "Error"})`);
      throw new Error(
        "Browser service unavailable. Check BROWSERLESS_TOKEN and the Browserless plan limits."
      );
    }
  }
  if (process.env.VERCEL) {
    throw new Error(
      "BROWSERLESS_TOKEN is not configured. Add it in the Vercel project environment variables."
    );
  }
  return puppeteer.launch(localLaunchOptions());
}

// ─── Short-lived extraction cache ────────────────────────────────────────────
// Switching servers used to relaunch the whole extraction every time. Caching
// the result for a few minutes makes switching back to a server instant while
// keeping the signed stream URLs fresh.

const EXTRACT_CACHE_TTL = 3 * 60 * 1000; // 3 minutes
const extractCache = new Map(); // key -> { data, t }

function getCachedExtract(key) {
  const hit = extractCache.get(key);
  if (!hit) return null;
  if (Date.now() - hit.t > EXTRACT_CACHE_TTL) {
    extractCache.delete(key);
    return null;
  }
  return hit.data;
}

function setCachedExtract(key, data) {
  if (extractCache.size >= 50) {
    extractCache.delete(extractCache.keys().next().value);
  }
  extractCache.set(key, { data, t: Date.now() });
}

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

// ─── In-memory media cache ────────────────────────────────────────────────────

const MEDIA_CACHE_TTL = 10 * 60 * 1000;
const MAX_CACHE_ENTRIES = 180;
const MAX_CACHE_BYTES = 80 * 1024 * 1024;
const MAX_CACHE_ENTRY_BYTES = 20 * 1024 * 1024;
const segmentCache = new Map();
let segmentCacheBytes = 0;

function cacheGet(url) {
  const entry = segmentCache.get(url);
  if (!entry) return null;
  if (Date.now() - entry.storedAt > MEDIA_CACHE_TTL) {
    segmentCache.delete(url);
    segmentCacheBytes -= entry.buffer.length;
    return null;
  }
  return entry;
}

function cacheSet(url, buffer, contentType) {
  if (!buffer || !buffer.length || buffer.length > MAX_CACHE_ENTRY_BYTES) return;
  const existing = segmentCache.get(url);
  if (existing) {
    segmentCache.delete(url);
    segmentCacheBytes -= existing.buffer.length;
  }
  while (
    segmentCache.size >= MAX_CACHE_ENTRIES ||
    segmentCacheBytes + buffer.length > MAX_CACHE_BYTES
  ) {
    const oldestKey = segmentCache.keys().next().value;
    if (!oldestKey) break;
    const oldest = segmentCache.get(oldestKey);
    segmentCache.delete(oldestKey);
    segmentCacheBytes -= oldest?.buffer.length || 0;
  }
  segmentCache.set(url, {
    buffer,
    contentType,
    storedAt: Date.now(),
  });
  segmentCacheBytes += buffer.length;
}

async function prefetch(url) {
  if (cacheGet(url)) return;
  try {
    const res = await axios({
      method: "GET",
      url,
      responseType: "arraybuffer",
      timeout: 15000,
      headers: { ...CDN_HEADERS, "Accept-Encoding": "identity" },
      httpAgent,
      httpsAgent,
      maxContentLength: MAX_CACHE_ENTRY_BYTES,
      validateStatus: (status) => status >= 200 && status < 300,
    });
    cacheSet(
      url,
      Buffer.from(res.data),
      res.headers["content-type"] || "video/mp2t"
    );
  } catch (error) {
    void error;
  }
}

function parseTargetUrl(raw) {
  if (typeof raw !== "string" || !raw.trim()) return null;
  let url;
  try {
    url = new URL(raw.trim());
  } catch {
    return null;
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") return null;
  if (url.username || url.password) return null;
  const host = url.hostname.toLowerCase().replace(/^\[|\]$/g, "");
  if (
    host === "localhost" ||
    host.endsWith(".localhost") ||
    host === "::1" ||
    host === "0.0.0.0" ||
    host.startsWith("fc") ||
    host.startsWith("fd") ||
    host.startsWith("fe80")
  ) {
    return null;
  }
  const ipv4 = host.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4) {
    const parts = ipv4.slice(1).map(Number);
    if (parts.some((part) => part > 255)) return null;
    const [a, b] = parts;
    if (
      a === 0 ||
      a === 10 ||
      a === 127 ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 100 && b >= 64 && b <= 127)
    ) {
      return null;
    }
  }
  return url;
}

function classifyTarget(url, hint) {
  const cleanHint = String(hint || "").toLowerCase();
  if (cleanHint === "hls" || cleanHint === "dash") return cleanHint;
  const path = url.pathname.toLowerCase();
  if (path.endsWith(".m3u8")) return "hls";
  if (path.endsWith(".mpd")) return "dash";
  return "";
}

function proxyQueryUrl(target) {
  return `/api/proxy?url=${encodeURIComponent(target)}`;
}

function proxyPathUrl(target) {
  return `/api/proxy/${encodeURIComponent(target)}`;
}

function proxyBaseUrl(target) {
  const value = String(target).endsWith("/")
    ? String(target).slice(0, -1)
    : String(target);
  return `/api/proxy/${encodeURIComponent(value)}/`;
}

function proxyTemplateUrl(target) {
  const encoded = String(target)
    .split(/(\$[^$\s]+\$)/g)
    .map((part) =>
      /^\$[^$\s]+\$$/.test(part) ? part : encodeURIComponent(part)
    )
    .join("");
  return `/api/proxy/${encoded}`;
}

function targetFromRequest(req) {
  const original = req.originalUrl || "";
  const path = original.split("?")[0];
  if (path.startsWith("/api/proxy/")) {
    const suffix = path.slice("/api/proxy/".length);
    let decoded;
    try {
      decoded = decodeURIComponent(suffix);
    } catch {
      return "";
    }
    const rawQuery = original.includes("?")
      ? original.split("?").slice(1).join("?")
      : "";
    if (!rawQuery) return decoded;
    return `${decoded}${decoded.includes("?") ? "&" : "?"}${rawQuery}`;
  }
  return typeof req.query.url === "string" ? req.query.url.trim() : "";
}

function parseRange(value, size) {
  if (typeof value !== "string") return null;
  const match = value.match(/^bytes=(\d*)-(\d*)$/);
  if (!match) return null;
  const [, rawStart, rawEnd] = match;
  if (!rawStart && !rawEnd) return null;
  let start;
  let end;
  if (!rawStart) {
    const suffix = Number(rawEnd);
    if (!Number.isFinite(suffix) || suffix <= 0) return null;
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(rawStart);
    end = rawEnd ? Number(rawEnd) : size - 1;
  }
  if (!Number.isFinite(start) || !Number.isFinite(end)) return null;
  if (start > end || start >= size) return { invalid: true };
  return { start, end: Math.min(end, size - 1) };
}

function sendMedia(req, res, buffer, contentType, extraHeaders = {}) {
  res.setHeader("Content-Type", contentType || "application/octet-stream");
  res.setHeader("Accept-Ranges", "bytes");
  res.setHeader("Cache-Control", "private, max-age=3600");
  Object.entries(extraHeaders).forEach(([key, value]) => {
    if (value) res.setHeader(key, value);
  });
  const range = parseRange(req.headers.range, buffer.length);
  if (range?.invalid) {
    res.status(416).setHeader("Content-Range", `bytes */${buffer.length}`);
    return res.end();
  }
  if (range) {
    const chunk = buffer.subarray(range.start, range.end + 1);
    res.status(206);
    res.setHeader(
      "Content-Range",
      `bytes ${range.start}-${range.end}/${buffer.length}`
    );
    res.setHeader("Content-Length", chunk.length);
    return res.end(chunk);
  }
  res.setHeader("Content-Length", buffer.length);
  return res.end(buffer);
}

// ─── Stream reachability probe ────────────────────────────────────────────────
// Viduki hands out URLs for several servers, but a server can answer the API
// with 200 while its CDN host is down (Cloudflare 522, DNS failure, …). Returning
// such a URL makes hls.js fail with "stream error" even though the title is
// playable on another server. Probe each candidate before handing it to the UI.

const PROBE_TIMEOUT = 5000;

async function probeStream(url, kind) {
  try {
    if (kind === "mp4") {
      const res = await axios({
        method: "GET",
        url,
        responseType: "stream",
        timeout: PROBE_TIMEOUT,
        headers: { ...CDN_HEADERS, Range: "bytes=0-0" },
        httpAgent,
        httpsAgent,
        validateStatus: (s) => s >= 200 && s < 300,
      });
      if (res.data && typeof res.data.destroy === "function") res.data.destroy();
      return true;
    }

    const res = await axios({
      method: "GET",
      url,
      responseType: "text",
      timeout: PROBE_TIMEOUT,
      headers: CDN_HEADERS,
      httpAgent,
      httpsAgent,
      maxContentLength: 4 * 1024 * 1024,
      validateStatus: (s) => s >= 200 && s < 300,
    });

    const body = String(res.data || "");
    if (kind === "hls") return body.includes("#EXTM3U");
    if (kind === "dash") return body.includes("<MPD") || body.includes("<mpd");
    return false;
  } catch (err) {
    console.log(`[probe] ${kind} unreachable (${err.code || err.name}): ${String(url).slice(0, 130)}`);
    return false;
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
  res.setHeader("Cache-Control", "no-store");
  const { tmdb, type = "movie", season, episode } = req.query;
  const forcedSrv =
    typeof req.query.srv === "string" ? req.query.srv.trim().replace(/[^A-Za-z]/g, "") : "";
  // TMDB original_language of the title (e.g. "hi"). Hindi titles get Viduki's
  // HINDI servers tried before every other server.
  const originalLang =
    typeof req.query.lang === "string" ? req.query.lang.trim().toLowerCase() : "";
  const preferHindi = !forcedSrv && originalLang === "hi";

  if (!tmdb) {
    return res.status(400).json({ error: "Missing tmdb query param" });
  }

  const vidukiUrl =
    type === "tv" && season && episode
      ? `https://www.viduki.net/1/tv/${tmdb}/${season}/${episode}`
      : `https://www.viduki.net/1/movie/${tmdb}?color=e01621`;

  const cacheKey = `${type}:${tmdb}:${season || ""}:${episode || ""}:${forcedSrv || (preferHindi ? "auto-hi" : "auto")}`;
  const cached = getCachedExtract(cacheKey);
  if (cached) {
    console.log(`[extract] Cache hit: ${cacheKey}`);
    return res.json(cached);
  }

  console.log(`[extract] Loading: ${vidukiUrl} (req: ${req.url})`);

  let browser;
  try {
    browser = await getBrowser();

    const page = await browser.newPage();
    await page.setViewport({ width: 1280, height: 720 });
    await page.setUserAgent(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    );

    const candidates = {};          // kind ("hls"|"dash"|"mp4") -> { url, t }
    const srvResponses = new Map(); // srv name -> { t, status } of latest GET /main/movie/<id>?srv=<name>
    let forcedStatus = null;        // latest GET /main/movie status when a server is forced
    let reorderedServers = false;   // /main/servers was rewritten to put HINDI servers first

    // Bounded diagnostic log of everything the page fetched
    const requestLog = [];          // { method, url, type, status, t? }
    const MAX_LOG = 600;
    let consoleErrors = 0;

    function rememberRequest(entry) {
      entry.t = Date.now();
      if (requestLog.length < MAX_LOG) requestLog.push(entry);
    }

    function noteCandidate(kind, url) {
      // Always keep the LATEST URL per kind — the player can swap sources at
      // any time (server switch, fallback walk) and the freshest one is the
      // one that will play.
      const existed = !!candidates[kind];
      candidates[kind] = { url, t: Date.now() };
      console.log(`[extract] Detected ${kind.toUpperCase()} stream${existed ? " (updated)" : ""}: ${url.slice(0, 180)}`);
    }

    // Passive request listener — CDP Fetch handles the forced-server rewrite below.
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
    page.on("response", async (response) => {
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

        // Track per-server API results (GET only — OPTIONS preflights pollute the map).
        // In forced mode every srv request is rewritten to the same server, so the
        // URL still says "Leon" even though the response came from the forced server.
        try {
          const srvMatch = url.match(/\/main\/(?:movie|tv)\/[^/]+\/?.*\?[^#]*\bsrv=([A-Za-z]+)/);
          if (srvMatch && response.request().method() === "GET") {
            if (forcedSrv) forcedStatus = { status, t: Date.now() };
            else srvResponses.set(srvMatch[1], { t: Date.now(), status });
          }
        } catch {}

        // Passively snapshot the player's /main/servers list when it boots.
        // In Hindi-first mode the body we see here has already been reordered
        // by the CDP handler, which saves the original order to the cache.
        if (!reorderedServers && url.replace(/[?#].*$/, "").endsWith("/main/servers")) {
          try {
            const body = await response.text();
            const data = JSON.parse(body);
            if (Array.isArray(data) && data.length && data[0].name) updateServersCache(data);
          } catch {}
        }

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

    // ── Forced-server mode ──
    // Viduki's player always asks its API for servers in order (Leon, Jill, …).
    // Instead of clicking its UI panel (slow and fragile), rewrite every
    // /main/movie/<id>?srv=… request to the requested server before it leaves
    // the page. The player then loads that server's stream directly.
    if (forcedSrv) {
      const cdp = await page.createCDPSession();
      cdp.on("Fetch.requestPaused", async ({ requestId, request }) => {
        try {
          const original = request.url;
          const url = original.replace(/([?&]srv=)[^&]*/, `$1${forcedSrv}`);
          if (url !== original) console.log(`[extract] Rewrote ${original.slice(0, 120)} -> srv=${forcedSrv}`);
          await cdp.send("Fetch.continueRequest", { requestId, url });
        } catch (err) {
          console.log(`[extract] Fetch handler error: ${err.message}`);
          try { await cdp.send("Fetch.continueRequest", { requestId }); } catch {}
        }
      });
      await cdp.send("Fetch.enable", {
        patterns: [{ urlPattern: "https://api.viduki.net/main/*", requestStage: "Request" }],
      });
      console.log(`[extract] Fetch interception enabled for srv=${forcedSrv}`);
    }

    // ── Hindi-first mode ──
    // The player walks the servers in the order returned by /main/servers.
    // For a title whose TMDB original_language is Hindi, rewrite that list so
    // every HINDI server comes first; the player then tries them in turn and
    // only moves on to the remaining servers if none of them can play.
    if (preferHindi) {
      const cdp = await page.createCDPSession();
      cdp.on("Fetch.requestPaused", async (event) => {
        const { requestId, responseStatusCode, responseHeaders, responseErrorReason } = event;
        const isResponse = responseStatusCode !== undefined || responseHeaders !== undefined;
        try {
          if (!isResponse || responseErrorReason) {
            await cdp.send("Fetch.continueRequest", { requestId });
            return;
          }
          if (responseStatusCode < 200 || responseStatusCode >= 300) {
            await cdp.send("Fetch.continueRequest", { requestId });
            return;
          }

          const body = await cdp.send("Fetch.getResponseBody", { requestId });
          const raw = body.base64Encoded ? Buffer.from(body.body, "base64").toString("utf8") : body.body;
          const list = JSON.parse(raw);
          if (!Array.isArray(list) || !list.length) throw new Error("unexpected server list");

          const isHindi = (srv) => {
            const language = String(srv?.language || "").trim().toUpperCase();
            return language === "HINDI" || language === "HI";
          };
          const hindi = list.filter(isHindi);
          if (!hindi.length) {
            updateServersCache(list);
            await cdp.send("Fetch.continueRequest", { requestId });
            return;
          }

          // Keep the UI picker's copy in Viduki's original order.
          updateServersCache(list);
          reorderedServers = true;

          const ordered = [...hindi, ...list.filter((srv) => !isHindi(srv))];
          const payload = Buffer.from(JSON.stringify(ordered));
          const headers = (responseHeaders || [])
            .filter((h) => !["content-length", "content-encoding", "transfer-encoding", "etag"].includes(h.name.toLowerCase()))
            .concat([{ name: "content-length", value: String(payload.length) }]);

          await cdp.send("Fetch.fulfillRequest", {
            requestId,
            responseCode: responseStatusCode,
            responseHeaders: headers,
            body: payload.toString("base64"),
          });
          console.log(`[extract] Hindi-first servers: ${hindi.map((s) => s.name).join(", ")} (then ${list.length - hindi.length} others)`);
        } catch (err) {
          console.log(`[extract] Hindi-first reorder failed (${err.message}) — using Viduki's order`);
          try { await cdp.send("Fetch.continueRequest", { requestId }); } catch {}
        }
      });
      await cdp.send("Fetch.enable", {
        patterns: [{ urlPattern: "https://api.viduki.net/main/servers*", requestStage: "Response" }],
      });
      console.log(`[extract] Hindi-first mode enabled (lang=${originalLang})`);
    }

    // domcontentloaded is enough — the player boots on its own and we poll for
    // its stream below. Waiting for networkidle2 added ~10-20s per request.
    try {
      await page.goto(vidukiUrl, { waitUntil: "domcontentloaded", timeout: 20000 });
    } catch (navErr) {
      console.log(`[extract] goto did not settle (${navErr.message}) — continuing`);
    }

    let picked = null;
    let pickedKind = null;

    if (forcedSrv) {
      console.log(`[extract] FORCED branch, srv=${forcedSrv}`);
      const deadline = Date.now() + 25000;
      let mp4SeenAt = null;
      let notAvailable = false;

      while (Date.now() < deadline) {
        // The forced server answered with an error (404/500) — stop early.
        if (forcedStatus && forcedStatus.status >= 400) {
          notAvailable = true;
          break;
        }

        // Once the forced server's envelope is 2xx, accept the stream the player
        // loads from it. MP4 gets a short grace period in case HLS appears too.
        if (forcedStatus && forcedStatus.status >= 200 && forcedStatus.status < 300) {
          if (candidates.hls) { picked = candidates.hls; pickedKind = "hls"; break; }
          if (candidates.dash) { picked = candidates.dash; pickedKind = "dash"; break; }
          if (candidates.mp4) {
            if (mp4SeenAt === null) mp4SeenAt = Date.now();
            else if (Date.now() - mp4SeenAt > 3000) {
              picked = candidates.mp4;
              pickedKind = "mp4";
              break;
            }
          }
        }

        await sleep(250);
      }

      if (notAvailable) {
        return res.status(404).json({
          error: `Not available on server "${forcedSrv}" for this title.`,
        });
      }

      if (!picked) {
        console.error(`[extract] Forced server ${forcedSrv} did not produce a stream. forcedStatus=${JSON.stringify(forcedStatus)} candidates=${JSON.stringify(Object.keys(candidates))}`);
        return res.status(404).json({
          error: `Could not switch to server "${forcedSrv}" — no stream was loaded from it.`,
          diagnostics: {
            forcedStatus,
            candidates: Object.fromEntries(Object.entries(candidates).map(([k, v]) => [k, { t: v.t }])),
          },
        });
      }

      if (!(await probeStream(picked.url, pickedKind))) {
        return res.status(502).json({
          error: `Server "${forcedSrv}" returned a stream, but it is not responding right now.`,
        });
      }
    } else {
      console.log("[extract] DEFAULT branch");
      // ── Default mode: first HEALTHY stream the player plays wins ──
      // The player walks Viduki's servers in order, but some CDN hosts are
      // frequently unreachable while the Viduki API still returns 200. Probe
      // every candidate and keep listening until one actually responds — the
      // player will move on to another server/format when a stream fails.
      const badStreams = new Set();
      const deadline = Date.now() + 45000;
      let mp4GraceEnd = null;

      while (Date.now() < deadline) {
        const hls = candidates.hls;
        if (hls && !badStreams.has(hls.url)) {
          if (await probeStream(hls.url, "hls")) {
            picked = hls;
            pickedKind = "hls";
            break;
          }
          badStreams.add(hls.url);
          console.log(`[extract] Skipping unreachable HLS: ${hls.url.slice(0, 130)}`);
        }

        const dash = candidates.dash;
        if (dash && !badStreams.has(dash.url)) {
          if (await probeStream(dash.url, "dash")) {
            picked = dash;
            pickedKind = "dash";
            break;
          }
          badStreams.add(dash.url);
          console.log(`[extract] Skipping unreachable DASH: ${dash.url.slice(0, 130)}`);
        }

        const mp4 = candidates.mp4;
        if (mp4 && !badStreams.has(mp4.url)) {
          // MP4-only title: keep listening briefly in case a better format shows up,
          // then settle for the MP4 instead of failing.
          if (mp4GraceEnd === null) {
            mp4GraceEnd = Date.now() + 5000;
          } else if (Date.now() > mp4GraceEnd) {
            if (await probeStream(mp4.url, "mp4")) {
              picked = mp4;
              pickedKind = "mp4";
              break;
            }
            badStreams.add(mp4.url);
            mp4GraceEnd = null;
            console.log(`[extract] Skipping unreachable MP4: ${mp4.url.slice(0, 130)}`);
          }
        }

        await sleep(500);
      }
    }

    if (!picked) {
      const diagnostics = buildDiagnostics(requestLog, consoleErrors);
      console.error("[extract] No stream found. Diagnostics:");
      console.error(`  console errors: ${diagnostics.consoleErrors}`);
      console.error(`  requests captured: ${diagnostics.requestsCaptured}, hosts: ${diagnostics.hosts.join(", ")}`);
      diagnostics.serverApiCalls.forEach((c) => console.error(`  [api] ${c.status} ${c.url}`));
      diagnostics.mediaUrls.forEach((c) => console.error(`  [media] ${c}`));
      console.error("  last requests:");
      diagnostics.recentRequests.forEach((c) => console.error(`    ${c}`));
      console.error(`  page HTML still contains player? ${diagnostics.playerPresent}`);

      return res.status(502).json({
        error: "No playable stream found — Viduki's servers are not responding right now. Please try again in a moment.",
        diagnostics,
      });
    }

    // In forced mode the selected server is known. In default mode the player
    // walks servers in order; the srv whose GET answered 200 just before the
    // stream is the source.
    const streamType = pickedKind;
    const activeServer = forcedSrv || (() => {
      let best = null;
      for (const [name, res_] of srvResponses) {
        if (res_.status === 200 && res_.t <= picked.t && (!best || res_.t > best.t)) best = { name, t: res_.t };
      }
      return best ? best.name : null;
    })();

    const result = { streamUrl: picked.url, type: streamType, server: activeServer };
    setCachedExtract(cacheKey, result);
    console.log(`[extract] Result: srv=${activeServer} type=${streamType}`);
    res.json(result);

  } catch (err) {
    console.error("[extract] Error:", err.message);
    res.status(500).json({ error: err.message || "Extraction failed" });
  } finally {
    if (browser) {
      try {
        await browser.close();
      } catch {}
    }
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

// ─── 1b. SERVERS — the playable-server list for the UI picker ─────────────────

// Direct calls to api.viduki.net/main/servers WITHOUT the player's nonce
// headers return a decoy stream object, so the list is harvested from the
// extraction page boot instead (the player requests it on every load).
const serversCache = { data: null };
const FALLBACK_SERVERS = [
  { name: "Leon", language: "ENGLISH", mp4: false, upscale: true },
  { name: "Jill", language: "ENGLISH", mp4: false, upscale: true },
  { name: "Ada", language: "ENGLISH", mp4: false, upscale: true },
  { name: "Claire", language: "ENGLISH", mp4: false, upscale: true },
  { name: "Sherry", language: "ENGLISH", mp4: true, upscale: false },
  { name: "Ethan", language: "ENGLISH", mp4: false, upscale: true },
  { name: "Chris", language: "HINDI", mp4: false, upscale: true },
  { name: "Wesker", language: "HINDI", mp4: false, upscale: true },
  { name: "Grace", language: "HINDI", mp4: true, upscale: false },
  { name: "Ashley", language: "VIETNAM", mp4: false, upscale: true },
];

function normalizeServers(raw) {
  try {
    return raw.map((s) => ({
      name: s.name,
      language: s.language || "",
      mp4: !!(s.useSW_mp4 || s.useSW_hls),
      upscale: !!s.useUpscale,
    }));
  } catch {
    return null;
  }
}

function updateServersCache(rawList) {
  const normalized = normalizeServers(rawList);
  if (normalized && normalized.length) {
    const changed = JSON.stringify(serversCache.data) !== JSON.stringify(normalized);
    serversCache.data = normalized;
    if (changed) console.log(`[servers] list cached (${normalized.length} servers)`);
  }
}

app.get("/api/servers", (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  if (serversCache.data) {
    return res.json({ servers: serversCache.data });
  }
  return res.json({ servers: FALLBACK_SERVERS });
});


app.get("/api/media-info", async (req, res) => {
  const target = parseTargetUrl(req.query.url);
  if (!target) {
    return res.status(400).json({ error: "Invalid media URL" });
  }
  res.setHeader("Cache-Control", "no-store");
  try {
    const response = await axios({
      method: "GET",
      url: target.toString(),
      responseType: "stream",
      timeout: 9000,
      headers: {
        ...CDN_HEADERS,
        Range: "bytes=0-0",
        "Accept-Encoding": "identity",
      },
      httpAgent,
      httpsAgent,
      validateStatus: () => true,
      maxRedirects: 5,
    });
    const contentType = response.headers["content-type"] || "";
    if (response.data && typeof response.data.destroy === "function") {
      response.data.destroy();
    }
    return res.json({
      status: response.status,
      contentType,
      url: target.toString(),
    });
  } catch (error) {
    return res.status(502).json({
      error: "Could not inspect the media URL",
      details: error.message,
    });
  }
});

// ─── 2. PROXY — playlists, manifests, media ranges, and cache ────────────────

app.use("/api/proxy", (req, res, next) => {
  if (req.method !== "GET") return next();
  handleProxy(req, res).catch((error) => {
    console.error("[proxy] Error:", error.message);
    if (!res.headersSent) {
      res.status(502).json({ error: "Proxy failed" });
    } else {
      res.end();
    }
  });
});

async function handleProxy(req, res) {
  const target = parseTargetUrl(targetFromRequest(req));
  if (!target) {
    return res.status(400).json({ error: "Invalid media URL" });
  }

  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader(
    "Access-Control-Expose-Headers",
    "Content-Length, Content-Range, Accept-Ranges"
  );

  const targetUrl = target.toString();
  const hint = typeof req.query.type === "string" ? req.query.type : "";
  const kind = classifyTarget(target, hint);

  if (kind) {
    const response = await axios({
      method: "GET",
      url: targetUrl,
      responseType: "text",
      timeout: 20000,
      headers: {
        ...CDN_HEADERS,
        "Accept-Encoding": "identity",
      },
      httpAgent,
      httpsAgent,
      validateStatus: (status) => status >= 200 && status < 300,
      maxRedirects: 5,
      maxContentLength: 12 * 1024 * 1024,
    });

    if (kind === "hls") {
      const { rewritten, segmentUrls } = rewritePlaylist(
        String(response.data || ""),
        target
      );
      res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
      res.setHeader("Cache-Control", "no-store");
      res.send(rewritten);
      segmentUrls.slice(0, 6).forEach((url) => prefetch(url));
      return;
    }

    const rewritten = rewriteDashManifest(String(response.data || ""), target);
    res.setHeader("Content-Type", "application/dash+xml");
    res.setHeader("Cache-Control", "no-store");
    return res.send(rewritten);
  }

  const cached = cacheGet(targetUrl);
  if (cached) {
    return sendMedia(req, res, cached.buffer, cached.contentType);
  }

  const upstreamHeaders = {
    ...CDN_HEADERS,
    "Accept-Encoding": "identity",
  };
  if (req.headers.range) upstreamHeaders.Range = req.headers.range;
  if (req.headers["if-range"]) {
    upstreamHeaders["If-Range"] = req.headers["if-range"];
  }

  const response = await axios({
    method: "GET",
    url: targetUrl,
    responseType: "arraybuffer",
    timeout: 35000,
    headers: upstreamHeaders,
    httpAgent,
    httpsAgent,
    validateStatus: () => true,
    maxRedirects: 5,
    maxContentLength: 64 * 1024 * 1024,
    decompress: false,
  });

  const buffer = Buffer.from(response.data || []);
  const contentType = response.headers["content-type"] || "application/octet-stream";

  if (response.status >= 400) {
    res.status(response.status);
    res.setHeader("Content-Type", "text/plain; charset=utf-8");
    return res.end(`Upstream media request failed (${response.status})`);
  }

  if (response.status === 206) {
    res.status(206);
    res.setHeader("Content-Type", contentType);
    res.setHeader("Accept-Ranges", "bytes");
    if (response.headers["content-range"]) {
      res.setHeader("Content-Range", response.headers["content-range"]);
    }
    res.setHeader("Content-Length", buffer.length);
    return res.end(buffer);
  }

  if (!req.headers.range) {
    cacheSet(targetUrl, buffer, contentType);
  }

  return sendMedia(req, res, buffer, contentType, {
    ETag: response.headers.etag,
    "Last-Modified": response.headers["last-modified"],
  });
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function resolveMediaUrl(value, baseUrl) {
  try {
    return new URL(String(value || "").trim(), baseUrl).toString();
  } catch {
    return "";
  }
}

function isHttpUrl(value) {
  return /^https?:\/\//i.test(String(value || ""));
}

function rewritePlaylist(text, baseUrl) {
  const segmentUrls = [];

  const rewriteUri = (uri) => {
    const absolute = resolveMediaUrl(uri, baseUrl);
    if (!isHttpUrl(absolute)) return uri;
    if (!absolute.includes(".m3u8")) segmentUrls.push(absolute);
    return proxyQueryUrl(absolute);
  };

  const rewritten = String(text)
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return line;
      if (trimmed.startsWith("#")) {
        return line.replace(/URI="([^"]+)"/g, (_, uri) => {
          return `URI="${rewriteUri(uri)}"`;
        });
      }
      return rewriteUri(trimmed);
    })
    .join("\n");

  return { rewritten, segmentUrls };
}

function decodeXml(value) {
  return String(value).replace(
    /&(amp|lt|gt|quot|apos|#\d+|#x[\da-f]+);/gi,
    (entity, code) => {
      const lower = String(code).toLowerCase();
      if (lower === "amp") return "&";
      if (lower === "lt") return "<";
      if (lower === "gt") return ">";
      if (lower === "quot") return '"';
      if (lower === "apos") return "'";
      if (lower.startsWith("#x")) {
        return String.fromCharCode(parseInt(lower.slice(2), 16));
      }
      return String.fromCharCode(parseInt(lower.slice(1), 10));
    }
  );
}

function escapeXmlText(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function escapeXmlAttr(value) {
  return escapeXmlText(value)
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function parseXml(xml) {
  const root = { type: "root", children: [] };
  const stack = [root];
  const tokenRe =
    /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<\?[\s\S]*?\?>|<!DOCTYPE[^>]*>|<[^>]+>|[^<]+/g;
  let match;
  while ((match = tokenRe.exec(xml))) {
    const token = match[0];
    if (
      token.startsWith("<!--") ||
      token.startsWith("<![CDATA[") ||
      token.startsWith("<?") ||
      token.startsWith("<!DOCTYPE")
    ) {
      stack[stack.length - 1].children.push({ type: "raw", value: token });
      continue;
    }
    if (token.startsWith("</")) {
      const name = token.slice(2, -1).trim().split(/\s/)[0];
      for (let index = stack.length - 1; index > 0; index -= 1) {
        if (stack[index].name === name) {
          stack.length = index;
          break;
        }
      }
      continue;
    }
    if (token.startsWith("<")) {
      const selfClosing = /\/>\s*$/.test(token);
      const content = token.slice(1, selfClosing ? -2 : -1).trim();
      const nameMatch = content.match(/^([^\s/>]+)/);
      if (!nameMatch) continue;
      const node = {
        type: "element",
        name: nameMatch[1],
        attrs: [],
        children: [],
      };
      const attrSource = content.slice(nameMatch[0].length);
      const attrRe = /([^\s=/>]+)\s*=\s*("([^"]*)"|'([^']*)')/g;
      let attrMatch;
      while ((attrMatch = attrRe.exec(attrSource))) {
        node.attrs.push({
          name: attrMatch[1],
          value: decodeXml(attrMatch[3] ?? attrMatch[4] ?? ""),
        });
      }
      stack[stack.length - 1].children.push(node);
      if (!selfClosing) stack.push(node);
      continue;
    }
    stack[stack.length - 1].children.push({
      type: "text",
      value: decodeXml(token),
    });
  }
  return root;
}

function serializeXml(node) {
  if (node.type === "raw") return node.value;
  if (node.type === "text") return escapeXmlText(node.value);
  if (node.type === "root") {
    return node.children.map(serializeXml).join("");
  }
  const attrs = node.attrs
    .map((attr) => ` ${attr.name}="${escapeXmlAttr(attr.value)}"`)
    .join("");
  if (!node.children.length) return `<${node.name}${attrs}/>`;
  return `<${node.name}${attrs}>${node.children
    .map(serializeXml)
    .join("")}</${node.name}>`;
}

function getXmlAttr(node, name) {
  const target = name.toLowerCase();
  const attr = node.attrs.find((item) => item.name.toLowerCase() === target);
  return attr ? attr.value : "";
}

function setXmlAttr(node, name, value) {
  const target = name.toLowerCase();
  const attr = node.attrs.find((item) => item.name.toLowerCase() === target);
  if (attr) attr.value = value;
  else node.attrs.push({ name, value });
}

function normalizeBaseUrl(value) {
  try {
    const url = new URL(value);
    url.hash = "";
    if (!url.search && !url.pathname.endsWith("/")) {
      const last = url.pathname.split("/").pop() || "";
      if (!last.includes(".")) url.pathname += "/";
    }
    return url.toString();
  } catch {
    return value;
  }
}

function rewriteDashManifest(text, manifestUrl) {
  const root = parseXml(text);

  const walk = (node, currentBase) => {
    if (node.type === "root") {
      node.children.forEach((child) => walk(child, currentBase));
      return;
    }
    if (node.type !== "element") return;

    const localName = (item) => item.name.split(":").pop().toLowerCase();
    const tag = localName(node);
    let base = currentBase;
    const baseElements = node.children.filter(
      (child) => child.type === "element" && localName(child) === "baseurl"
    );

    baseElements.forEach((element, index) => {
      const value = element.children
        .filter((child) => child.type === "text")
        .map((child) => child.value)
        .join("")
        .trim();
      const absolute = resolveMediaUrl(value, currentBase);
      if (!isHttpUrl(absolute)) return;
      const normalized = normalizeBaseUrl(absolute);
      element.children = [
        { type: "text", value: proxyBaseUrl(normalized) },
      ];
      if (index === 0) base = normalized;
    });

    if (tag === "location" || tag === "patchlocation") {
      const value = node.children
        .filter((child) => child.type === "text")
        .map((child) => child.value)
        .join("")
        .trim();
      const absolute = resolveMediaUrl(value, currentBase);
      if (isHttpUrl(absolute)) {
        node.children = [{ type: "text", value: proxyPathUrl(absolute) }];
      }
      return;
    }

    if (tag === "segmenttemplate") {
      ["media", "initialization"].forEach((attrName) => {
        const value = getXmlAttr(node, attrName);
        if (!value) return;
        const absolute = resolveMediaUrl(value, base);
        if (isHttpUrl(absolute)) {
          setXmlAttr(node, attrName, proxyTemplateUrl(absolute));
        }
      });
    }

    if (tag === "segmenturl") {
      const value = getXmlAttr(node, "media");
      const absolute = resolveMediaUrl(value, base);
      if (isHttpUrl(absolute)) {
        setXmlAttr(node, "media", proxyTemplateUrl(absolute));
      }
    }

    if (tag === "initialization") {
      const value = getXmlAttr(node, "sourceurl");
      const absolute = resolveMediaUrl(value, base);
      if (isHttpUrl(absolute)) {
        setXmlAttr(node, "sourceURL", proxyTemplateUrl(absolute));
      }
    }

    node.children.forEach((child) => {
      if (child.type === "element" && localName(child) === "baseurl") return;
      walk(child, base);
    });
  };

  walk(root, manifestUrl);
  return serializeXml(root);
}

// ─── Start ────────────────────────────────────────────────────────────────────

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`✅ WatchAll proxy server running at http://localhost:${PORT} [${BUILD}]`);
    console.log(
      `   Media cache: up to ${MAX_CACHE_ENTRIES} objects / ${Math.round(MAX_CACHE_BYTES / (1024 * 1024))} MB`
    );
    console.log(`   Keep-alive CDN connections: enabled`);
    console.log(`   Playlist prefetch: 6 segments ahead`);
  });
}

module.exports = app;
