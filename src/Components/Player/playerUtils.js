const TV_PATTERN =
  /(smart-?tv|smarttv|tizen|webos|web0s|netcast|android tv|googletv|google tv|fire ?tv|aft[bsm]|bravia|hbbtv|opera tv|viera|roku|appletv)/i;

export const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

export function formatTime(value) {
  if (!Number.isFinite(value) || value < 0) return "0:00";
  const total = Math.floor(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function formatYear(dateString) {
  const match = String(dateString || "").match(/^(\d{4})/);
  return match ? match[1] : "";
}

export function formatRuntime(minutes) {
  const total = Number(minutes);
  if (!Number.isFinite(total) || total <= 0) return "";
  const hours = Math.floor(total / 60);
  const mins = Math.round(total % 60);
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m`;
}

export function readStore(key, fallback) {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

export function writeStore(key, value) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    void error;
  }
}

export function removeStore(key) {
  try {
    window.localStorage.removeItem(key);
  } catch (error) {
    void error;
  }
}

export const resumeKey = (mediaId) => `watchall:resume:${mediaId}`;
export const prefsKey = "watchall:player:prefs";
export const captionsKey = "watchall:player:captions";
export const captionSizeKey = "watchall:player:caption-size";

export const alternateServerId = "Vidnest";

export function serverDisplayName(name) {
  return name === alternateServerId ? "Server List 2" : name;
}

export function getDeviceProfile() {
  const ua = navigator.userAgent || "";
  const params = new URLSearchParams(window.location.search);
  const forceTv = params.get("tv") === "1" || params.get("tv") === "true";
  const tvLike = window.innerWidth >= 1280 && window.matchMedia("(hover: none) and (pointer: none)").matches;
  const isTV = forceTv || TV_PATTERN.test(ua) || tvLike;
  const isIOS =
    /iPad|iPhone|iPod/.test(ua) ||
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const isTouch =
    window.matchMedia("(pointer: coarse)").matches || navigator.maxTouchPoints > 0;
  const width = window.innerWidth;
  const isMobile = !isTV && (width <= 768 || (isTouch && width <= 900));
  const isTablet = !isTV && !isMobile && isTouch && width <= 1180;
  const memory = Number(navigator.deviceMemory) || 4;
  const cores = Number(navigator.hardwareConcurrency) || 4;
  const lowPower = isTV ? memory <= 3 || cores <= 4 : memory <= 2 || cores <= 2;
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  return {
    isTV,
    isIOS,
    isTouch,
    isMobile,
    isTablet,
    isDesktop: !isTV && !isMobile && !isTablet,
    lowPower,
    reducedMotion,
    isHiDPi: window.devicePixelRatio >= 2,
  };
}

export function detectSourceType(url, hint) {
  const cleanHint = String(hint || "").toLowerCase();
  if (cleanHint === "hls" || cleanHint === "dash" || cleanHint === "mp4") {
    return cleanHint;
  }
  const path = String(url || "").split(/[?#]/)[0].toLowerCase();
  if (path.endsWith(".m3u8")) return "hls";
  if (path.endsWith(".mpd")) return "dash";
  if (/\.(mp4|m4v|webm|ogv|ogg|mov)$/.test(path)) return "mp4";
  return "";
}

export function proxyUrl(url, type = "", referer = "") {
  const hint = type ? `&type=${encodeURIComponent(type)}` : "";
  const ref = referer ? `&referer=${encodeURIComponent(referer)}` : "";
  return `/api/proxy?url=${encodeURIComponent(url)}${hint}${ref}`;
}

export function streamUrlFor(type, url, { forceProxy = false, referer = "" } = {}) {
  // Progressive MP4 always goes through the proxy: it guarantees Range
  // requests, correct cross-origin headers and a consistent seek experience.
  if (forceProxy || type === "mp4" || (import.meta.env.DEV && type !== "mp4")) {
    return proxyUrl(url, type, referer);
  }
  return url;
}

let hevcSupport = null;

// HEVC (H.265) gives the same picture at roughly half the bitrate. Browsers
// expose support through MediaSource, so check once and cache the result.
export function deviceSupportsHevc() {
  if (hevcSupport !== null) return hevcSupport;
  try {
    const mediaSource =
      typeof window !== "undefined"
        ? window.MediaSource || window.ManagedMediaSource
        : null;
    const probe = (mime) =>
      (mediaSource && typeof mediaSource.isTypeSupported === "function"
        ? mediaSource.isTypeSupported(mime)
        : false) ||
      (typeof document !== "undefined" &&
        !!document.createElement("video").canPlayType(mime));
    hevcSupport =
      probe('video/mp4; codecs="hev1.1.6.L93.B0"') ||
      probe('video/mp4; codecs="hvc1.1.6.L93.B0"');
  } catch {
    hevcSupport = false;
  }
  return hevcSupport;
}

export function codecKind(value) {
  const text = String(value || "").toLowerCase();
  if (/hev1|hvc1|hevc|h\.?265|x265/.test(text)) return "hevc";
  if (/avc1|avc3|h\.?264|x264/.test(text)) return "h264";
  if (/av01|av1/.test(text)) return "av1";
  if (/vp0?9/.test(text)) return "vp9";
  return "";
}

export function heightFromUrl(value) {
  const text = String(value || "");
  const match = text.match(/(?:^|[^\d])(2160|1440|1080|720|480|360|240)p(?:[^\d]|$)/i);
  if (match) return Number(match[1]);
  if (/\b(4k|uhd)\b/i.test(text)) return 2160;
  if (/\bfhd\b/i.test(text)) return 1080;
  if (/\bhd\b/i.test(text)) return 720;
  return 0;
}

// Turns the server's `streamUrl` + `hls`/`dash`/`streams` block into one
// ordered fallback chain. The proven stream is always first.
export function buildStreamCandidates(data) {
  if (!data) return [];
  const list = [];
  const push = (url, type, extra = {}) => {
    if (!url) return;
    if (list.some((item) => item.url === url)) return;
    list.push({
      url,
      type: type || detectSourceType(url, type) || "hls",
      quality: Number(extra.quality || extra.height || heightFromUrl(url)) || 0,
      codec: extra.codec || codecKind(url),
      verified: !!extra.verified,
      label: extra.label || "",
    });
  };

  push(data.streamUrl, data.type, { verified: true });
  (Array.isArray(data.candidates) ? data.candidates : []).forEach((item) => {
    if (item && typeof item === "object") {
      push(item.url, item.type, item);
    }
  });
  push(data.dash, "dash");
  push(data.hls, "hls");
  (Array.isArray(data.streams) ? data.streams : []).forEach((item) => {
    if (typeof item === "string") push(item, "mp4");
    else if (item && item.url) push(item.url, item.type || "mp4", item);
  });
  push(data.mp4, "mp4");
  return list;
}

export function parseSubtitleText(text) {
  const source = String(text || "").replace(/\r\n/g, "\n").trim();
  if (!source) return [];
  const isSrt = /^\d+\s*\n\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->/.test(source);
  const blocks = source.split(/\n{2,}/);
  const toSeconds = (value) => {
    const match = String(value)
      .trim()
      .match(/(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})/);
    if (!match) return 0;
    return (
      Number(match[1]) * 3600 +
      Number(match[2]) * 60 +
      Number(match[3]) +
      Number(match[4].padEnd(3, "0")) / 1000
    );
  };
  const cues = [];
  blocks.forEach((block) => {
    const lines = block.split("\n");
    if (isSrt && /^\d+$/.test(lines[0].trim())) lines.shift();
    const timingIndex = lines.findIndex((line) => line.includes("-->"));
    if (timingIndex < 0) return;
    const timing = lines[timingIndex];
    const [startRaw, endRaw] = timing.split("-->");
    const start = toSeconds(startRaw);
    const end = toSeconds(endRaw);
    if (!(end > start)) return;
    const cueText = lines
      .slice(timingIndex + 1)
      .join("\n")
      .replace(/<[^>]+>/g, "")
      .trim();
    if (!cueText) return;
    cues.push({ start, end, text: cueText });
  });
  return cues;
}

export async function sniffSourceType(url) {
  try {
    const response = await fetch(`/api/media-info?url=${encodeURIComponent(url)}`);
    if (!response.ok) return "";
    const data = await response.json();
    const contentType = String(data.contentType || "").toLowerCase();
    if (contentType.includes("mpegurl") || contentType.includes("m3u8")) return "hls";
    if (contentType.includes("dash+xml") || contentType.includes("mpd")) return "dash";
    if (contentType.startsWith("video/") || contentType.includes("octet-stream")) return "mp4";
    return "";
  } catch {
    return "";
  }
}

export function qualityLabel(track) {
  if (!track) return "Auto";
  if (track.height >= 2160) return "4K";
  if (track.height >= 1080) return `${track.height}p`;
  if (track.height) return `${track.height}p`;
  if (track.bitrate) return `${Math.round(track.bitrate / 1000)} kbps`;
  return `Level ${Number(track.id) + 1}`;
}

export function trackLabel(track, index, prefix) {
  if (!track) return `${prefix} ${index + 1}`;
  return track.label || track.name || track.language || track.lang || `${prefix} ${index + 1}`;
}

const AUDIO_LANGUAGE_ALIASES = {
  hi: ["hi", "hin", "hindi"],
  en: ["en", "eng", "english"],
  ta: ["ta", "tam", "tamil"],
  te: ["te", "tel", "telugu"],
  ml: ["ml", "mal", "malayalam"],
  kn: ["kn", "kan", "kannada"],
  bn: ["bn", "ben", "bengali"],
  mr: ["mr", "mar", "marathi"],
  gu: ["gu", "guj", "gujarati"],
  pa: ["pa", "pan", "punjabi"],
  ur: ["ur", "urd", "urdu"],
  or: ["or", "ori", "odia", "oriya"],
  as: ["as", "asm", "assamese"],
  ne: ["ne", "nep", "nepali"],
  si: ["si", "sin", "sinhala", "sinhalese"],
  fr: ["fr", "fre", "fra", "french"],
  es: ["es", "spa", "spanish"],
  de: ["de", "ger", "deu", "german"],
  ja: ["ja", "jpn", "japanese"],
  ko: ["ko", "kor", "korean"],
  zh: ["zh", "chi", "zho", "chinese", "mandarin"],
  ar: ["ar", "ara", "arabic"],
  ru: ["ru", "rus", "russian"],
  pt: ["pt", "por", "portuguese"],
  tr: ["tr", "tur", "turkish"],
  id: ["id", "ind", "indonesian"],
  th: ["th", "tha", "thai"],
  vi: ["vi", "vie", "vietnamese"],
};

export function audioTrackMatchesLanguage(track, language) {
  const requested = String(language || "")
    .trim()
    .toLowerCase()
    .split(/[-_]/)[0];
  if (!requested) return false;
  const aliases = AUDIO_LANGUAGE_ALIASES[requested] || [requested];
  const values = [track?.language, track?.lang, track?.label, track?.name]
    .filter(Boolean)
    .map((value) => String(value).trim().toLowerCase());
  return values.some((value) =>
    aliases.some((alias) => {
      if (value === alias) return true;
      if (value.startsWith(`${alias}-`) || value.startsWith(`${alias}_`)) {
        return true;
      }
      return alias.length > 2 && new RegExp(`\\b${alias}\\b`).test(value);
    })
  );
}

export function hlsConfigFor(profile) {
  const constrained = profile.lowPower || profile.isTV;
  return {
    enableWorker: !profile.lowPower,
    lowLatencyMode: true,
    startFragPrefetch: true,
    backBufferLength: constrained ? 30 : 60,
    maxBufferLength: constrained ? 24 : 45,
    maxMaxBufferLength: constrained ? 45 : 90,
    maxBufferSize: (constrained ? 30 : 80) * 1000 * 1000,
    capLevelToPlayerSize: !profile.isTV,
    testBandwidth: true,
    // Start on the lowest track and let ABR upgrade once real throughput is
    // measured. This avoids a long stall when a fast connection is assumed.
    startLevel: 0,
    abrEwmaDefaultEstimate: constrained ? 700_000 : 1_000_000,
  };
}

export function maxQualityIndex(tracks, maxHeight) {
  let cap = -1;
  tracks.forEach((track, index) => {
    if (!track.height || track.height <= maxHeight) cap = index;
  });
  return cap;
}
