/**
 * Vidnest stream extraction.
 *
 * Vidnest pages only load their player inside an embedded frame, so the page is
 * opened through the app's own /api/embed wrapper. The logic here is the same
 * detection engine used by standalone-stream-extractor.cjs: watch every network
 * response, keep media candidates, gently nudge the player, then return the best
 * confirmed non-ad stream.
 */

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

const HLS_RE = /\.(m3u8|m3u)([?#]|$)/i;
const DASH_RE = /\.mpd([?#]|$)/i;
const SEGMENT_RE =
  /\.(ts|m4s|aac|vtt|srt|key|jpg|jpeg|png|gif|webp|ico|css|js|woff2?|ttf|map)([?#]|$)/i;
const INIT_RE =
  /(?:^|\/)(?:init|init-stream\d*|segment|seg|chunk|frag|part)[-_]?\d*\.(?:mp4|m4s|m4v)([?#]|$)/i;

const AD_MARKERS =
  /(^|[/._?&=-])(ad|ads|advert|advertisement|preroll|pre-roll|vast|vpaid|ad[-_]?site|ad[-_]?break)([/._?&=-]|$)/i;
const AD_HOSTS =
  /(^|\.)(doubleclick\.net|googlesyndication\.com|amazon-adsystem\.com|adnxs\.com|taboola\.com|outbrain\.com)$/i;

const PLAY_BUTTON_SELECTORS = [
  ".vjs-big-play-button",
  ".vjs-play-control",
  ".plyr__control--overlaid",
  ".plyr__control[data-plyr='play']",
  ".jw-icon-playback",
  ".jw-display-icon-container",
  "[data-plyr='play']",
  "[data-testid*='play' i]",
  "[class*='play-button' i]",
  "[class*='playbtn' i]",
  "[class*='play-btn' i]",
  "[class*='play-icon' i]",
  "[class*='vds-play' i]",
  "[class*='play-toggle' i]",
  "[part='play-button']",
  "media-play-button",
  "[aria-label*='play' i]",
  "[title*='play' i]",
  "[class*='play' i]",
  "[class*='poster' i]",
];

const SKIP_BUTTON_SELECTORS = [
  "[class*='skip' i]",
  "[id*='skip' i]",
  "[aria-label*='skip' i]",
  "[title*='skip' i]",
  "button",
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function isHttpUrl(value) {
  return /^https?:\/\//i.test(String(value || ""));
}

function parseTarget(raw) {
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
    host === "::" ||
    host === "0.0.0.0"
  ) {
    return null;
  }
  if (host.includes(":")) {
    if (host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")) {
      return null;
    }
    return url;
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

function typeFromUrl(raw) {
  let parsed;
  try {
    parsed = new URL(raw);
  } catch {
    return "";
  }
  const pathname = parsed.pathname.toLowerCase();
  const full = parsed.toString().toLowerCase();
  if (HLS_RE.test(pathname)) return "hls";
  if (DASH_RE.test(pathname)) return "dash";
  if (/\.(mp4|m4v)$/.test(pathname)) return "mp4";
  if (/\.webm$/.test(pathname)) return "webm";
  if (/\.(mkv|mov)$/.test(pathname)) return "file";
  if (/[?&](?:format|type)=m3u8\b/.test(full)) return "hls";
  if (/[?&](?:format|type)=mpd\b/.test(full)) return "dash";
  return "";
}

function typeFromContentType(contentType) {
  const ct = String(contentType || "").toLowerCase();
  if (!ct) return "";
  if (ct.includes("mpegurl")) return "hls";
  if (ct.includes("dash+xml")) return "dash";
  if (ct.includes("video/mp4") || ct.includes("video/quicktime")) return "mp4";
  if (ct.includes("video/webm")) return "webm";
  if (ct.includes("video/x-matroska")) return "file";
  return "";
}

function isAdLike(raw) {
  try {
    const url = new URL(raw);
    if (AD_HOSTS.test(url.hostname)) return true;
    const text = decodeURIComponent(url.pathname + url.search);
    if (AD_MARKERS.test(text)) return true;
  } catch {}
  return false;
}

function noteCandidate(candidates, url, type, confirmed, size) {
  if (!isHttpUrl(url)) return false;
  if (url.length > 4096) return false;
  if (SEGMENT_RE.test(url) || INIT_RE.test(url)) return false;
  const existing = candidates.get(url);
  if (existing) {
    existing.lastSeen = Date.now();
    if (confirmed && !existing.confirmed) {
      existing.confirmed = true;
      if (size > existing.size) existing.size = size;
      return true;
    }
    if (size > existing.size) existing.size = size;
    return false;
  }
  candidates.set(url, {
    url,
    type,
    confirmed: Boolean(confirmed),
    adLike: isAdLike(url),
    size: Number(size) || 0,
    firstSeen: Date.now(),
    lastSeen: Date.now(),
  });
  return true;
}

function isNamedMaster(url) {
  try {
    return /(^|\/)master\.m3u8?$/i.test(new URL(url).pathname);
  } catch {
    return false;
  }
}

function rankCandidates(candidates) {
  const order = { hls: 0, dash: 1, mp4: 2, webm: 3, file: 4 };
  const list = Array.from(candidates.values()).filter(
    (candidate) => !SEGMENT_RE.test(candidate.url) && !INIT_RE.test(candidate.url)
  );
  list.sort((a, b) => {
    if (a.adLike !== b.adLike) return a.adLike ? 1 : -1;
    if (a.confirmed !== b.confirmed) return a.confirmed ? -1 : 1;
    const rankA = order[a.type] ?? 9;
    const rankB = order[b.type] ?? 9;
    if (rankA !== rankB) return rankA - rankB;
    if (a.type === "hls" && b.type === "hls") {
      const masterA = isNamedMaster(a.url);
      const masterB = isNamedMaster(b.url);
      if (masterA !== masterB) return masterA ? -1 : 1;
    }
    if (rankA >= 2 && a.size !== b.size) return b.size - a.size;
    return b.lastSeen - a.lastSeen;
  });
  return list;
}

async function fetchProbe(url, type, referer) {
  try {
    if (type === "hls" || type === "dash") {
      const response = await fetch(url, {
        headers: baseHeaders(referer),
        redirect: "follow",
        signal: AbortSignal.timeout(9000),
      });
      if (!response.ok) return { ok: false, drm: false };
      const text = (await response.text()).slice(0, 500000);
      if (type === "hls") {
        return {
          ok: text.includes("#EXTM3U"),
          drm:
            /#EXT-X-(?:SESSION-)?KEY:[^\n]*(?:METHOD=SAMPLE-AES|METHOD=SAMPLE-AES-CTR|KEYFORMAT="com\.apple\.streamingkeydelivery")/i.test(
              text
            ),
        };
      }
      return {
        ok: /<MPD[\s>]/i.test(text),
        drm: /<ContentProtection[\s>]/i.test(text),
      };
    }
    const response = await fetch(url, {
      method: "GET",
      headers: { ...baseHeaders(referer), Range: "bytes=0-0" },
      redirect: "follow",
      signal: AbortSignal.timeout(9000),
    });
    if (response.body) {
      try {
        await response.body.cancel();
      } catch {}
    }
    return { ok: response.status >= 200 && response.status < 300, drm: false };
  } catch {
    return { ok: false, drm: false };
  }
}

async function inspectDirect(pageUrl, referer) {
  const hint = typeFromUrl(pageUrl);
  if (hint) {
    try {
      const response = await fetch(pageUrl, {
        method: "GET",
        headers: { ...baseHeaders(referer), Range: "bytes=0-0" },
        redirect: "follow",
        signal: AbortSignal.timeout(8000),
      });
      const contentType = response.headers.get("content-type") || "";
      const finalUrl = response.url || pageUrl;
      if (response.body) {
        try {
          await response.body.cancel();
        } catch {}
      }
      if (contentType.toLowerCase().includes("text/html")) return null;
      const contentKind = typeFromContentType(contentType);
      if (contentKind) {
        return { url: finalUrl, type: contentKind, direct: true };
      }
      return { url: finalUrl, type: hint, direct: true };
    } catch {
      return { url: pageUrl, type: hint, direct: true };
    }
  }
  try {
    const response = await fetch(pageUrl, {
      method: "GET",
      headers: { ...baseHeaders(referer), Range: "bytes=0-1" },
      redirect: "follow",
      signal: AbortSignal.timeout(8000),
    });
    const contentType = (response.headers.get("content-type") || "").toLowerCase();
    const finalUrl = response.url || pageUrl;
    if (response.body) {
      try {
        await response.body.cancel();
      } catch {}
    }
    if (contentType.startsWith("text/html")) return null;
    const kind = typeFromContentType(contentType);
    if (kind) return { url: finalUrl, type: kind, direct: true };
  } catch {}
  return null;
}

function rememberRequest(log, entry) {
  if (log.length >= 400) log.shift();
  log.push(entry);
}

function findPendingRequest(log, url) {
  for (let index = log.length - 1; index >= 0; index -= 1) {
    if (log[index].url === url && log[index].status === null) return log[index];
  }
  return null;
}

async function isVideoPlaying(page) {
  for (const frame of page.frames()) {
    if (frame.isDetached()) continue;
    try {
      const playing = await frame.evaluate(() =>
        Array.from(document.querySelectorAll("video")).some(
          (video) => !video.paused && video.readyState >= 2
        )
      );
      if (playing) return true;
    } catch {}
  }
  return false;
}

async function findSkipTarget(page) {
  const viewport = page.viewport() || { width: 1366, height: 768 };
  for (const frame of page.frames()) {
    if (frame.isDetached()) continue;
    const handles = [];
    for (const selector of SKIP_BUTTON_SELECTORS) {
      try {
        handles.push(...(await frame.$$(selector)));
      } catch {}
    }
    for (const handle of handles.slice(0, 24)) {
      try {
        const info = await handle.evaluate((element) => {
          const rect = element.getBoundingClientRect();
          const style = getComputedStyle(element);
          const label = (
            (element.getAttribute("aria-label") || "") +
            " " +
            (element.getAttribute("title") || "") +
            " " +
            (element.textContent || "") +
            " " +
            String(element.className || "")
          ).toLowerCase();
          return {
            visible:
              rect.width > 8 &&
              rect.height > 8 &&
              style.display !== "none" &&
              style.visibility !== "hidden" &&
              Number(style.opacity || 1) > 0.01 &&
              rect.bottom > 0 &&
              rect.right > 0 &&
              rect.top < window.innerHeight &&
              rect.left < window.innerWidth,
            label,
          };
        });
        if (!info.visible || !/\bskip\b/.test(info.label) || /intro/.test(info.label)) {
          continue;
        }
        const box = await handle.boundingBox();
        if (!box || box.width < 8 || box.height < 8) continue;
        if (box.x > viewport.width || box.y > viewport.height) continue;
        if (box.x + box.width < 0 || box.y + box.height < 0) continue;
        return { x: box.x + box.width / 2, y: box.y + box.height / 2, kind: "skip" };
      } catch {}
    }
  }
  return null;
}

async function findPlayTarget(page) {
  const viewport = page.viewport() || { width: 1366, height: 768 };
  try {
    await page.mouse.move(Math.round(viewport.width / 2), Math.round(viewport.height / 2));
  } catch {}
  await sleep(150);
  for (const frame of page.frames()) {
    if (frame.isDetached()) continue;
    const query = async (selector) => {
      try {
        return await frame.$$(selector);
      } catch {
        return [];
      }
    };
    const playHandles = [];
    for (const selector of PLAY_BUTTON_SELECTORS) {
      playHandles.push(...(await query(selector)));
      if (playHandles.length >= 16) break;
    }
    playHandles.push(...(await query("button, [role='button'], a")).slice(0, 10));
    for (const handle of playHandles) {
      try {
        const info = await handle.evaluate((element) => {
          const rect = element.getBoundingClientRect();
          const style = getComputedStyle(element);
          const label = (
            (element.getAttribute("aria-label") || "") +
            " " +
            (element.getAttribute("title") || "") +
            " " +
            (element.textContent || "") +
            " " +
            String(element.className || "")
          ).toLowerCase();
          return {
            visible:
              rect.width > 18 &&
              rect.height > 18 &&
              style.display !== "none" &&
              style.visibility !== "hidden" &&
              Number(style.opacity || 1) > 0.01 &&
              rect.bottom > 0 &&
              rect.right > 0 &&
              rect.top < window.innerHeight &&
              rect.left < window.innerWidth,
            label,
            tag: element.tagName.toLowerCase(),
          };
        });
        if (!info.visible) continue;
        if (
          ["button", "a", "div", "span"].includes(info.tag) &&
          !/\b(play|watch|start|resume)\b/.test(info.label)
        ) {
          continue;
        }
        const box = await handle.boundingBox();
        if (!box || box.width < 18 || box.height < 18) continue;
        if (box.x > viewport.width || box.y > viewport.height) continue;
        if (box.x + box.width < 0 || box.y + box.height < 0) continue;
        return { x: box.x + box.width / 2, y: box.y + box.height / 2, kind: "button" };
      } catch {}
    }
    const videoHandles = await query("video");
    for (const handle of videoHandles) {
      try {
        const info = await handle.evaluate((element) => {
          const rect = element.getBoundingClientRect();
          return {
            paused: element.paused,
            visible: rect.width > 20 && rect.height > 20,
          };
        });
        if (!info.visible || !info.paused) continue;
        await handle.evaluate((element) => {
          try {
            element.muted = true;
            element.play().catch(() => {});
          } catch {}
        });
        await sleep(300);
        let alreadyPlaying = false;
        try {
          alreadyPlaying = await handle.evaluate(
            (element) => !element.paused && element.readyState >= 2
          );
        } catch {}
        const box = await handle.boundingBox();
        if (box && box.width > 20 && box.height > 20) {
          return {
            x: box.x + box.width / 2,
            y: box.y + box.height / 2,
            kind: "video",
            alreadyPlaying,
          };
        }
      } catch {}
    }
    const iframeHandles = await query("iframe");
    let bestIframe = null;
    for (const handle of iframeHandles) {
      try {
        const box = await handle.boundingBox();
        if (!box || box.width < 120 || box.height < 80) continue;
        if (box.x > viewport.width || box.y > viewport.height) continue;
        if (box.x + box.width < 0 || box.y + box.height < 0) continue;
        const area = box.width * box.height;
        if (!bestIframe || area > bestIframe.area) {
          bestIframe = {
            x: box.x + box.width / 2,
            y: box.y + box.height / 2,
            area,
            kind: "iframe",
          };
        }
      } catch {}
    }
    if (bestIframe) return bestIframe;
    const containerHandles = await query("[class*='player' i], [id*='player' i], canvas");
    let bestContainer = null;
    for (const handle of containerHandles.slice(0, 12)) {
      try {
        const box = await handle.boundingBox();
        if (!box || box.width < 160 || box.height < 90) continue;
        if (box.x > viewport.width || box.y > viewport.height) continue;
        if (box.x + box.width < 0 || box.y + box.height < 0) continue;
        const area = box.width * box.height;
        if (!bestContainer || area > bestContainer.area) {
          bestContainer = {
            x: box.x + box.width / 2,
            y: box.y + box.height / 2,
            area,
            kind: "container",
          };
        }
      } catch {}
    }
    if (bestContainer) return bestContainer;
  }
  return null;
}

async function nudgePlayback(page, state) {
  if (page.isClosed()) return false;
  const now = Date.now();
  if (now - state.lastAt < 2000 || state.clicks >= 10) return false;
  state.lastAt = now;

  const skip = await findSkipTarget(page);
  if (skip) {
    try {
      await page.mouse.move(skip.x, skip.y);
      await page.mouse.click(skip.x, skip.y);
      state.clicks += 1;
      state.skipped = true;
      return true;
    } catch {}
  }

  if (state.started) {
    if (await isVideoPlaying(page)) return false;
    state.started = false;
  }

  const target = await findPlayTarget(page);
  if (target) {
    if (target.alreadyPlaying) {
      state.started = true;
      return true;
    }
    try {
      await page.mouse.move(target.x, target.y);
      await page.mouse.click(target.x, target.y);
      state.clicks += 1;
      state.lastKind = target.kind;
      await sleep(600);
      if (await isVideoPlaying(page)) state.started = true;
      return true;
    } catch {}
  }

  if (state.attempts < 2) {
    state.attempts += 1;
    const viewport = page.viewport() || { width: 1366, height: 768 };
    try {
      await page.mouse.click(Math.round(viewport.width / 2), Math.round(viewport.height / 2));
      state.clicks += 1;
      await sleep(500);
      if (await isVideoPlaying(page)) state.started = true;
      return true;
    } catch {}
  }
  return false;
}

async function extractStream(pageUrl, options) {
  const {
    waitMs = 20000,
    referer = "",
    embedUrl = "",
    getBrowser,
    isCancelled = () => false,
  } = options;
  const startedAt = Date.now();
  const effectiveReferer = referer || pageUrl;

  const direct = await inspectDirect(pageUrl, effectiveReferer);
  if (direct) {
    const now = Date.now();
    return {
      streamUrl: direct.url,
      type: direct.type,
      confirmed: true,
      direct: true,
      pageUrl,
      referer: effectiveReferer,
      candidates: [
        {
          url: direct.url,
          type: direct.type,
          confirmed: true,
          size: 0,
          firstSeen: now,
          lastSeen: now,
        },
      ],
      requests: [],
      consoleErrors: 0,
      elapsedMs: now - startedAt,
      drm: false,
      autoStart: { clicks: 0, started: false },
    };
  }

  const browser = await getBrowser();
  try {
    const page = await browser.newPage();
    await page.setUserAgent(UA);
    await page.setViewport({ width: 1366, height: 768 });

    const candidates = new Map();
    const requests = [];
    const nudgeStates = new Map();
    let consoleErrors = 0;
    let lastNewAt = Date.now();
    let firstPrimaryAt = 0;
    let nextNudgeAt = Date.now() + 1200;

    page.on("request", (request) => {
      const url = request.url();
      if (!isHttpUrl(url)) return;
      rememberRequest(requests, {
        method: request.method(),
        url,
        type: request.resourceType(),
        status: null,
        at: Date.now(),
      });
      const kind = typeFromUrl(url);
      if (!kind) return;
      if (SEGMENT_RE.test(url) || INIT_RE.test(url)) return;
      const resourceType = request.resourceType();
      if (kind === "mp4" || kind === "webm" || kind === "file") {
        if (!["media", "navigate", "xhr", "fetch"].includes(resourceType)) return;
      }
      if (noteCandidate(candidates, url, kind, false, 0)) lastNewAt = Date.now();
    });

    page.on("response", (response) => {
      try {
        const url = response.url();
        if (!isHttpUrl(url)) return;
        let status = 0;
        let headers = {};
        let contentType = "";
        try {
          status = response.status();
          headers = response.headers();
          contentType = (headers["content-type"] || "").toLowerCase();
        } catch {}
        const pending = findPendingRequest(requests, url);
        if (pending) pending.status = status;
        else {
          rememberRequest(requests, {
            method: "GET",
            url,
            type: "response",
            status,
            at: Date.now(),
          });
        }
        if (status < 200 || status >= 300) return;
        if (contentType.startsWith("text/html")) return;
        if (SEGMENT_RE.test(url) || INIT_RE.test(url)) return;
        const size = Number(headers["content-length"] || 0) || 0;
        const contentKind = typeFromContentType(contentType);
        const extensionKind = typeFromUrl(url);

        if (contentKind === "hls" || contentKind === "dash") {
          if (noteCandidate(candidates, url, contentKind, true, size)) lastNewAt = Date.now();
          return;
        }

        if (extensionKind && ["mp4", "webm", "file"].includes(extensionKind)) {
          const playable =
            contentType.startsWith("video/") ||
            contentType.includes("octet-stream") ||
            !contentType;
          if (playable) {
            if (noteCandidate(candidates, url, extensionKind, true, size)) lastNewAt = Date.now();
          }
          return;
        }

        if (contentKind && ["mp4", "webm", "file"].includes(contentKind)) {
          if (noteCandidate(candidates, url, contentKind, true, size)) lastNewAt = Date.now();
        }
      } catch {}
    });

    page.on("requestfailed", (request) => {
      rememberRequest(requests, {
        method: request.method(),
        url: request.url(),
        type: "failed",
        status: null,
        error: request.failure()?.errorText || "",
        at: Date.now(),
      });
    });

    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors += 1;
    });

    const navUrl = embedUrl || pageUrl;
    const navigationTimeout = Math.min(30000, Math.max(10000, waitMs + 6000));
    try {
      await page.goto(navUrl, {
        waitUntil: "domcontentloaded",
        timeout: navigationTimeout,
      });
    } catch {}

    const deadline = Date.now() + waitMs;
    while (Date.now() < deadline) {
      if (isCancelled()) break;
      const ranked = rankCandidates(candidates);
      const content = ranked.filter(
        (candidate) => candidate.confirmed && !candidate.adLike
      );
      const primary = content.find(
        (candidate) => candidate.type === "hls" || candidate.type === "dash"
      );
      if (primary) {
        if (!firstPrimaryAt) firstPrimaryAt = Date.now();
        if (Date.now() - firstPrimaryAt >= 4000 && Date.now() - lastNewAt >= 6000) break;
      } else if (content.length) {
        if (Date.now() - lastNewAt >= 8000) break;
      }
      if (!content.length && Date.now() >= nextNudgeAt) {
        const state =
          nudgeStates.get(page) || {
            clicks: 0,
            lastAt: 0,
            attempts: 0,
            started: false,
          };
        nudgeStates.set(page, state);
        await nudgePlayback(page, state);
        nextNudgeAt = Date.now() + 2600;
      }
      await sleep(250);
    }

    const ranked = rankCandidates(candidates);
    if (!ranked.length) {
      throw new Error(
        "The server list did not load any playable stream. The title may be unavailable or a verification step may be required."
      );
    }

    let picked = ranked[0];
    let drm = false;

    if (picked.type === "hls" || picked.type === "dash") {
      const probe = await fetchProbe(picked.url, picked.type, effectiveReferer);
      drm = probe.drm;
    }

    const elapsedMs = Date.now() - startedAt;
    const autoStartClicks = Array.from(nudgeStates.values()).reduce(
      (total, state) => total + state.clicks,
      0
    );
    const autoStarted = Array.from(nudgeStates.values()).some((state) => state.started);
    return {
      streamUrl: picked.url,
      type: picked.type,
      confirmed: Boolean(picked.confirmed),
      adLike: Boolean(picked.adLike),
      direct: false,
      embed: Boolean(embedUrl),
      pageUrl,
      referer: effectiveReferer,
      candidates: ranked.map((candidate) => ({
        url: candidate.url,
        type: candidate.type,
        confirmed: Boolean(candidate.confirmed),
        adLike: Boolean(candidate.adLike),
        size: candidate.size,
        firstSeen: candidate.firstSeen,
        lastSeen: candidate.lastSeen,
      })),
      autoStart: { clicks: autoStartClicks, started: autoStarted },
      requests: requests.slice(-140),
      consoleErrors,
      elapsedMs,
      drm,
    };
  } finally {
    try {
      await browser.close();
    } catch {}
  }
}

async function extractVidnest({ tmdb, getBrowser, embedUrl, waitMs = 20000 }) {
  const id = String(tmdb || "").trim();
  if (!/^\d{1,10}$/.test(id)) {
    throw new Error("A numeric TMDB id is required.");
  }
  const pageUrl = `https://vidnest.fun/movie/${id}`;
  if (!parseTarget(pageUrl)) {
    throw new Error("The requested page address is not valid.");
  }

  const result = await extractStream(pageUrl, {
    waitMs,
    referer: pageUrl,
    embedUrl,
    getBrowser,
  });

  if (result.drm) {
    throw new Error("The stream is DRM-protected and cannot be played here.");
  }
  if (!result.confirmed || result.adLike) {
    throw new Error(
      "Only an unconfirmed or advertisement stream was found. Try Server List 1."
    );
  }

  return {
    streamUrl: result.streamUrl,
    type: result.type,
    server: "Vidnest",
    source: "vidnest",
    referer: result.referer,
    forceProxy: true,
    confirmed: true,
    elapsedMs: result.elapsedMs,
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

module.exports = { extractVidnest, buildEmbedPage };
