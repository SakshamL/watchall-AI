/**
 * VidNest stream extraction.
 *
 * VidNest used to expose the video URL directly from its player page, so this
 * module opened the page in a headless browser and watched network traffic.
 * The site now resolves every title through its own API:
 *
 *   https://new.vidnest.fun/<provider>/{movie|tv}/<tmdb>[/<season>/<episode>]?_n=<nonce>
 *
 * The API requires the `_n` nonce (any random 16-byte base64url value works)
 * and usually answers with an AES-256-GCM encrypted envelope:
 *
 *   { "v": 2, "d": "<base64url(iv + ciphertext + tag)>", "n": "<key nonce>" }
 *
 * The key is SHA-256("<payload.n>|<shared secret>") and the IV is the first
 * 12 bytes of the decoded payload. This module performs that handshake
 * directly, tries every provider, verifies the returned media, and returns the
 * best playable stream plus alternates. It is much faster and far more reliable
 * than the old browser probe, which timed out when the default provider was
 * down and could not recognise extensionless HLS URLs such as `master.txt`.
 */

const crypto = require("crypto");

const SITE_BASE = "https://vidnest.fun";
const API_BASE = "https://new.vidnest.fun";
const SHARED_SECRET = "K3p8vQ2mX9rL5wZ0fN7bG1sH4tJ6dC9_y8S3wM1nQ5pL2";
const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

/**
 * VidNest provider keys and their API routes. The order is only a mild
 * tie-breaker: every provider is queried in parallel, because any one of them
 * can be temporarily offline (the site itself walks this same list).
 */
const PROVIDERS = [
  { key: "catflix", name: "Catflix", movie: "/yflix/movie", tv: "/yflix/tv" },
  { key: "beta", name: "Beta", movie: "/flixhq/movie", tv: "/flixhq/tv" },
  { key: "lamda", name: "Lamda", movie: "/ophim/movie", tv: "/ophim/tv" },
  { key: "zeta", name: "Zeta", movie: "/nextgencloudfabric/movie", tv: "/nextgencloudfabric/tv" },
  { key: "alfa", name: "Alfa", movie: "/onehd/movie", tv: "/onehd/tv" },
  { key: "filxer", name: "Filxer", movie: "/rogflix/movie", tv: "/rogflix/tv" },
  { key: "delta", name: "Delta", movie: "/allmovies/movie", tv: "/allmovies/tv" },
  { key: "gama", name: "Gama", movie: "/vidzee/movie", tv: "/vidzee/tv" },
  { key: "sigma", name: "Sigma", movie: "/hollymoviehd/movie", tv: "/hollymoviehd/tv" },
  { key: "ophim", name: "Ophim", movie: "/klikxxi/movie", tv: "/klikxxi/tv" },
  { key: "prime", name: "Prime", movie: "/rpmvid/movie", tv: "/rpmvid/tv" },
  { key: "hexa", name: "Hexa", movie: "/vidlink/movie", tv: "/vidlink/tv" },
];

const MAX_CONCURRENT_PROVIDERS = 12;
const MAX_PROBED_CANDIDATES = 8;
const PROVIDER_TIMEOUT_MS = 9000;
const PROBE_TIMEOUT_MS = 8000;

/**
 * VidNest's provider API (new.vidnest.fun) sits behind a Cloudflare rule that
 * rejects requests from datacenter networks — including Vercel's serverless
 * egress, which answers 403 immediately. The site's own player relays API calls
 * through one of these endpoints, which we use only when the direct call fails,
 * so local development still talks to the API directly.
 */
const RELAY_BASES = [
  "https://megacloud.animanga.fun/ts-proxy",
  "https://upcloud.animanga.fun/ts-proxy",
];

// On Vercel the direct API always answers 403, so do not spend the full
// provider timeout on it before falling back to the relay.
const ON_VERCEL = Boolean(process.env.VERCEL);

/**
 * VidNest's own front end rewrites relative provider paths (Alfa does this)
 * through one of these Cloudflare workers, so we mirror that behaviour.
 */
const PROXY_HOSTS = [
  "https://long-night-d802.streamed-5.workers.dev",
  "https://blue-bush-5977.streamed-7.workers.dev",
  "https://rapid-heart-03c8.streamed-4.workers.dev",
  "https://super-flower-9c85.streamed-2.workers.dev",
  "https://flat-sky-cf20.streamed-850.workers.dev",
  "https://vidrift-cf.streamed-8.workers.dev",
];
let proxyHostIndex = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function isHttpUrl(value) {
  return /^https?:\/\//i.test(String(value || ""));
}

function resolveUrl(raw) {
  const value = String(raw || "").trim();
  if (!value) return "";
  if (value.startsWith("//")) return `https:${value}`;
  if (isHttpUrl(value)) return value;

  // Some providers (Alfa especially) hand back paths such as `/hls/...`.
  // The site itself prefixes every relative URL with one of its Cloudflare
  // workers (see eR in the player bundle), so mirror that here instead of
  // guessing the API origin.
  const host = PROXY_HOSTS[proxyHostIndex % PROXY_HOSTS.length];
  proxyHostIndex += 1;
  return `${host}${value.startsWith("/") ? value : `/${value}`}`;
}

function makeNonce() {
  // 16 random bytes, base64url without padding — exactly what the site sends.
  return crypto
    .randomBytes(16)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function cleanHeaders(input) {
  const out = {};
  if (!input || typeof input !== "object") return out;
  for (const [key, value] of Object.entries(input)) {
    if (value == null) continue;
    const name = String(key).trim();
    const text = String(value).trim();
    if (!name || !text) continue;
    out[name] = text;
  }
  return out;
}

function baseHeaders(referer) {
  const headers = {
    "User-Agent": UA,
    Accept: "*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "identity",
  };
  if (referer) {
    try {
      const ref = new URL(referer);
      headers.Referer = ref.toString();
      headers.Origin = ref.origin;
    } catch {
      headers.Referer = referer;
    }
  }
  return headers;
}

function decryptPayload(payload) {
  if (!payload || typeof payload !== "object") return payload;
  if (payload.v !== 2 || typeof payload.d !== "string" || !payload.d) return payload;

  const keyMaterial = `${payload.n || ""}|${SHARED_SECRET}`;
  const key = crypto.createHash("sha256").update(keyMaterial).digest();
  let raw;
  try {
    raw = Buffer.from(String(payload.d).replace(/-/g, "+").replace(/_/g, "/"), "base64");
  } catch {
    throw new Error("VidNest returned an unreadable response.");
  }
  if (raw.length <= 28) throw new Error("VidNest returned an unreadable response.");

  const iv = raw.subarray(0, 12);
  const body = raw.subarray(12, raw.length - 16);
  const tag = raw.subarray(raw.length - 16);

  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  let text;
  try {
    text = Buffer.concat([decipher.update(body), decipher.final()]).toString("utf8");
  } catch {
    throw new Error("VidNest response could not be decrypted.");
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function parseQuality(value) {
  const text = String(value || "").toLowerCase();
  if (!text) return 0;
  if (/\b(4k|uhd)\b/.test(text)) return 2160;
  const match = text.match(/(\d{3,4})/);
  if (!match) return 0;
  const height = Number(match[1]);
  return Number.isFinite(height) && height >= 140 ? height : 0;
}

function typeFromUrl(url, declared) {
  const hint = String(declared || "").trim().toLowerCase();
  if (hint) {
    if (hint.includes("mpegurl") || hint === "hls" || hint === "m3u8") return "hls";
    if (hint.includes("dash+xml") || hint === "mpd" || hint === "dash") return "dash";
    if (hint === "mp4" || hint === "video" || hint === "m4v") return "mp4";
    if (hint === "webm") return "mp4";
  }
  let path = "";
  try {
    path = new URL(url).pathname.toLowerCase();
  } catch {
    path = String(url || "").toLowerCase();
  }
  if (/\.(m3u8|m3u)$/.test(path)) return "hls";
  if (/\.mpd$/.test(path)) return "dash";
  if (/\.(mp4|m4v|webm)$/.test(path)) return "mp4";
  // Matroska is played as a progressive file by ExoPlayer; the web player may
  // reject it, so it ranks below real HLS/MP4 streams.
  if (/\.mkv$/.test(path)) return "mp4";
  // VidNest hides some HLS manifests behind extensionless routes such as
  // ".../master.txt", ".../pl/<token>/master.m3u8" or "0-21?e=...".
  if (/master\.txt$/i.test(path) && /master|index/i.test(path)) return "hls";
  if (/\/(?:pl|hls|proxy)\//.test(path) && /master|index|\.txt$/i.test(path)) return "hls";
  return "";
}

function isMatroska(url) {
  try {
    return /\.mkv$/i.test(new URL(url).pathname);
  } catch {
    return false;
  }
}

function collectStreams(payload, provider) {
  const streams = [];
  if (!payload || typeof payload !== "object") {
    // Some responses are a bare URL string.
    if (typeof payload === "string" && isHttpUrl(payload)) {
      const url = resolveUrl(payload);
      const type = typeFromUrl(url, "");
      if (type) {
        streams.push({
          url,
          type,
          quality: 0,
          language: "",
          referer: "",
          headers: {},
          providerKey: provider.key,
          providerName: provider.name,
        });
      }
    }
    return streams;
  }

  const add = (raw, fallbackType) => {
    if (!raw) return;
    let item = raw;
    if (typeof item === "string") item = { url: item, type: fallbackType };
    if (!item || typeof item !== "object") return;
    const rawUrl = item.url || item.link || item.file || item.playlist || item.src;
    const url = resolveUrl(rawUrl);
    if (!url || !isHttpUrl(url)) return;
    const headers = cleanHeaders(item.headers);
    const referer = headers.Referer || headers.referer || "";
    const type = typeFromUrl(url, item.type || fallbackType);
    if (!type) return;
    streams.push({
      url,
      type,
      quality: parseQuality(item.quality || item.height || item.label || item.resolution),
      language: String(item.language || item.lang || item.audio || "").trim(),
      referer,
      headers,
      providerKey: provider.key,
      providerName: provider.name,
    });
  };

  if (Array.isArray(payload.streams)) payload.streams.forEach((item) => add(item));
  if (Array.isArray(payload.sources)) payload.sources.forEach((item) => add(item));
  if (Array.isArray(payload.data?.streams)) payload.data.streams.forEach((item) => add(item));
  if (Array.isArray(payload.data?.sources)) payload.data.sources.forEach((item) => add(item));
  if (payload.url) add({ ...payload, url: payload.url }, payload.type);
  if (payload.data?.stream?.playlist) {
    add(
      {
        url: payload.data.stream.playlist,
        type: payload.data.stream.type || "hls",
        headers: payload.headers,
      },
      "hls"
    );
  }
  if (typeof payload.playlist === "string") add({ url: payload.playlist, type: "hls" }, "hls");

  // Deduplicate by URL, keeping the highest reported quality.
  const seen = new Map();
  for (const stream of streams) {
    const existing = seen.get(stream.url);
    if (!existing) {
      seen.set(stream.url, stream);
    } else if (stream.quality > existing.quality) {
      existing.quality = stream.quality;
    }
  }
  return Array.from(seen.values());
}

const PROVIDER_HEADERS = {
  "User-Agent": UA,
  Accept: "application/json, text/plain, */*",
  "Accept-Language": "en-US,en;q=0.9",
  Referer: `${SITE_BASE}/`,
  Origin: SITE_BASE,
};

async function readJson(response) {
  let text;
  try {
    text = (await response.text()).slice(0, 2_000_000);
  } catch {
    return null;
  }
  if (!text.trim()) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/**
 * Fetch one provider. Direct first; if the direct call is blocked (Vercel gets
 * an instant Cloudflare 403) or answers with something that is not JSON, retry
 * through the relay hosts the VidNest site itself uses.
 */
async function fetchProvider(provider, options) {
  const { tmdb, type, season, episode, timeoutMs } = options;
  const path =
    type === "tv"
      ? `${provider.tv}/${encodeURIComponent(tmdb)}/${encodeURIComponent(season)}/${encodeURIComponent(episode)}`
      : `${provider.movie}/${encodeURIComponent(tmdb)}`;
  const url = `${API_BASE}${path}?_n=${encodeURIComponent(makeNonce())}`;

  let parsed = null;
  try {
    const response = await fetch(url, {
      headers: { ...PROVIDER_HEADERS, "X-Stream-Nonce": makeNonce() },
      redirect: "follow",
      signal: AbortSignal.timeout(ON_VERCEL ? 3000 : timeoutMs),
    });
    if (response.ok) parsed = await readJson(response);
  } catch {
    parsed = null;
  }

  if (!parsed) {
    for (const relay of RELAY_BASES) {
      try {
        const relayUrl = `${relay}?url=${encodeURIComponent(url)}`;
        const response = await fetch(relayUrl, {
          headers: { "User-Agent": UA, Accept: "application/json, text/plain, */*" },
          redirect: "follow",
          signal: AbortSignal.timeout(timeoutMs),
        });
        if (!response.ok) continue;
        parsed = await readJson(response);
        if (parsed) break;
      } catch {
        // Try the next relay.
      }
    }
  }

  if (!parsed) return [];

  let payload;
  try {
    payload = decryptPayload(parsed);
  } catch {
    return [];
  }
  return collectStreams(payload, provider);
}

async function mapWithConcurrency(items, limit, worker) {
  const results = [];
  let index = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (index < items.length) {
      const current = index;
      index += 1;
      try {
        results[current] = await worker(items[current], current);
      } catch {
        results[current] = [];
      }
    }
  });
  await Promise.all(runners);
  return results;
}

function languageRank(language, originalLang) {
  const text = String(language || "").toLowerCase();
  // Providers often leave the language blank. Treat that as "original" rather
  // than penalising it, so quality decides between unlabelled sources.
  if (!text) return 0;
  if (text.includes("original")) return 0;
  const wanted = String(originalLang || "").toLowerCase();
  const matches =
    (wanted === "hi" && (text.includes("hindi") || text.includes("original"))) ||
    (wanted && text.startsWith(wanted)) ||
    (wanted === "en" && text.includes("english"));
  if (matches) return 0;
  return 2;
}

function typeRank(stream) {
  if (stream.type === "hls") return 0;
  if (stream.type === "dash") return 1;
  return isMatroska(stream.url) ? 3 : 2;
}

function rankStreams(streams, originalLang) {
  const providerOrder = new Map(PROVIDERS.map((provider, index) => [provider.key, index]));
  return [...streams].sort((a, b) => {
    const languageDiff = languageRank(a.language, originalLang) - languageRank(b.language, originalLang);
    if (languageDiff !== 0) return languageDiff;
    const typeDiff = typeRank(a) - typeRank(b);
    if (typeDiff !== 0) return typeDiff;
    if (a.quality !== b.quality) return b.quality - a.quality;
    return (providerOrder.get(a.providerKey) ?? 99) - (providerOrder.get(b.providerKey) ?? 99);
  });
}

async function probeStream(stream) {
  const referer = stream.referer || `${SITE_BASE}/`;
  const headers = {
    ...baseHeaders(referer),
    ...stream.headers,
    "Accept-Encoding": "identity",
  };
  if (stream.type === "mp4") headers.Range = "bytes=0-1";

  let response;
  try {
    response = await fetch(stream.url, {
      headers,
      redirect: "follow",
      signal: AbortSignal.timeout(PROBE_TIMEOUT_MS),
    });
  } catch {
    return { ok: false, quality: 0 };
  }
  if (!response.ok) return { ok: false, quality: 0 };

  const contentType = (response.headers.get("content-type") || "").toLowerCase();
  const manifestLike =
    stream.type === "hls" ||
    stream.type === "dash" ||
    contentType.includes("mpegurl") ||
    contentType.includes("dash+xml") ||
    /\.(m3u8|m3u|txt)$/i.test(stream.url.split("?")[0]);

  try {
    if (manifestLike) {
      const text = (await response.text()).slice(0, 200_000);
      const isDash = stream.type === "dash" || /<MPD[\s>]/i.test(text);
      const isHls = text.includes("#EXTM3U");
      if (!isDash && !isHls) return { ok: false, quality: 0 };

      // Read the real ladder from the manifest so quality ranking does not
      // depend on what the provider happened to put in its metadata.
      let height = 0;
      if (isHls) {
        for (const match of text.matchAll(/RESOLUTION=\d+x(\d+)/gi)) {
          height = Math.max(height, Number(match[1]) || 0);
        }
      } else {
        for (const match of text.matchAll(/(?:height|maxHeight)="(\d+)"/gi)) {
          height = Math.max(height, Number(match[1]) || 0);
        }
      }
      return { ok: true, quality: height, dash: isDash };
    }
    if (response.body) await response.body.cancel();
  } catch {
    return { ok: false, quality: 0 };
  }

  const ok =
    contentType.startsWith("video/") ||
    contentType.includes("octet-stream") ||
    contentType.includes("matroska") ||
    response.status === 206;
  return { ok, quality: 0 };
}

function sameReferer(a, b) {
  const left = String(a || "").trim();
  const right = String(b || "").trim();
  if (!left || !right) return true;
  return left === right;
}

async function extractVidnest({ tmdb, type = "movie", season, episode, lang, timeoutMs = 20000 }) {
  const startedAt = Date.now();
  const id = String(tmdb || "").trim();
  if (!/^\d{1,10}$/.test(id)) {
    throw new Error("A numeric TMDB id is required.");
  }
  const mediaType = String(type || "movie").trim().toLowerCase() === "tv" ? "tv" : "movie";
  const seasonValue = String(season || "").trim();
  const episodeValue = String(episode || "").trim();
  if (mediaType === "tv" && (!/^\d+$/.test(seasonValue) || !/^\d+$/.test(episodeValue))) {
    throw new Error("This TV episode is missing its season or episode number.");
  }

  const budgetMs = Math.max(6000, Math.min(45000, Number(timeoutMs) || 20000));
  const providerTimeout = Math.min(PROVIDER_TIMEOUT_MS, budgetMs);

  const responses = await mapWithConcurrency(
    PROVIDERS,
    MAX_CONCURRENT_PROVIDERS,
    (provider) =>
      fetchProvider(provider, {
        tmdb: id,
        type: mediaType,
        season: seasonValue,
        episode: episodeValue,
        timeoutMs: providerTimeout,
      })
  );

  const deduped = new Map();
  for (const stream of responses.flat()) {
    if (!stream?.url || deduped.has(stream.url)) continue;
    deduped.set(stream.url, stream);
  }
  if (!deduped.size) {
    throw new Error(
      "The server list did not load any playable stream. The title may be unavailable or a verification step may be required."
    );
  }

  const shortlist = rankStreams(Array.from(deduped.values()), lang).slice(0, MAX_PROBED_CANDIDATES * 2);
  const checks = await Promise.all(shortlist.map((stream) => probeStream(stream)));
  const alive = shortlist
    .map((stream, index) => {
      const check = checks[index];
      if (!check?.ok) return null;
      return {
        ...stream,
        type: check.dash ? "dash" : stream.type,
        quality: Math.max(stream.quality, check.quality || 0),
      };
    })
    .filter(Boolean);
  if (!alive.length) {
    throw new Error(
      "The server list did not load any playable stream. The title may be unavailable or a verification step may be required."
    );
  }

  const finalists = rankStreams(alive, lang);
  const primary = finalists[0];
  const referer = primary.referer || `${SITE_BASE}/`;
  const candidates = finalists
    .filter((stream) => sameReferer(stream.referer, primary.referer))
    .slice(0, MAX_PROBED_CANDIDATES);

  return {
    streamUrl: primary.url,
    type: primary.type,
    server: "Vidnest",
    source: "vidnest",
    provider: primary.providerKey,
    serverName: primary.providerName,
    referer,
    forceProxy: true,
    confirmed: true,
    elapsedMs: Date.now() - startedAt,
    candidates: candidates.map((stream) => ({
      url: stream.url,
      type: stream.type,
      quality: stream.quality,
      verified: true,
    })),
  };
}

function buildEmbedPage(src) {
  const safe = String(src).replace(/[&<>"']/g, (character) => {
    return (
      {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      }[character] || character
    );
  });
  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="referrer" content="origin-when-cross-origin">
<style>
html,body{margin:0;height:100%;background:#000;overflow:hidden}
iframe{width:100vw;height:100vh;border:0;display:block}
</style>
</head>
<body>
<iframe src="${safe}" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen frameborder="0" scrolling="no"></iframe>
</body>
</html>`;
}

module.exports = {
  extractVidnest,
  buildEmbedPage,
  // Exported for the small local verification script.
  __test: { decryptPayload, collectStreams, typeFromUrl, rankStreams },
};
