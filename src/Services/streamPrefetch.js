import { useCallback, useEffect, useRef } from "react";
import { buildStreamCandidates } from "../Components/Player/playerUtils";

// Stream URLs are signed and short-lived, so prefetched results only make sense
// for a couple of minutes. The server keeps its own 3 minute extraction cache,
// which means a hover prefetch is usually served instantly when the user clicks.
const PREFETCH_TTL = 2.5 * 60 * 1000;
const FAILURE_TTL = 30 * 1000;
const MAX_QUEUE = 4;
const MAX_ACTIVE = 1;
const REQUEST_TIMEOUT = 60 * 1000;

const cache = new Map();
const queue = [];
const inFlight = new Set();
let active = 0;

const now = () => Date.now();

const isEnabled = () => {
  if (typeof window === "undefined") return false;
  try {
    if (document.visibilityState === "hidden") return false;
    const connection =
      navigator.connection ||
      navigator.mozConnection ||
      navigator.webkitConnection;
    if (connection?.saveData) return false;
    if (connection?.effectiveType === "2g") return false;
  } catch {
    return false;
  }
  return true;
};

const idle = (callback, timeout = 5000) => {
  if (typeof window.requestIdleCallback === "function") {
    return window.requestIdleCallback(callback, { timeout });
  }
  return window.setTimeout(callback, Math.min(timeout, 1200));
};

export function prefetchKey({
  mediaId,
  mediaType = "movie",
  source = "viduki",
  language = "",
  server = "",
}) {
  return [
    source,
    mediaType,
    mediaId,
    String(language || "").toLowerCase(),
    String(server || "").toLowerCase(),
  ].join(":");
}

function readEntry(key) {
  const entry = cache.get(key);
  if (!entry) return null;
  const ttl = entry.data ? PREFETCH_TTL : FAILURE_TTL;
  if (now() - entry.at > ttl) {
    cache.delete(key);
    return null;
  }
  return entry;
}

/** Returns prefetched extraction data without removing it from the cache. */
export function peekPrefetchedStream(key) {
  const entry = readEntry(key);
  return entry?.data ? entry.data : null;
}

/** Removes a prefetched result after the player has consumed it. */
export function consumePrefetchedStream(key) {
  const entry = readEntry(key);
  if (!entry?.data) return null;
  cache.delete(key);
  return entry.data;
}

function pump() {
  while (active < MAX_ACTIVE && queue.length) {
    const job = queue.shift();
    if (!job || inFlight.has(job.key) || readEntry(job.key)) continue;
    if (!isEnabled()) {
      queue.length = 0;
      return;
    }
    run(job);
  }
}

function run(job) {
  active += 1;
  inFlight.add(job.key);
  const controller =
    typeof AbortController !== "undefined" ? new AbortController() : null;
  const timer = window.setTimeout(() => controller?.abort(), REQUEST_TIMEOUT);

  const url =
    `/api/extract?tmdb=${encodeURIComponent(job.mediaId)}` +
    `&type=${encodeURIComponent(job.mediaType)}` +
    `&source=${encodeURIComponent(job.source)}` +
    (job.language ? `&lang=${encodeURIComponent(job.language)}` : "") +
    (job.server ? `&srv=${encodeURIComponent(job.server)}` : "");

  fetch(url, { signal: controller?.signal })
    .then(async (response) => {
      const data = await response.json().catch(() => null);
      if (!response.ok || !data?.streamUrl) {
        cache.set(job.key, { at: now(), data: null });
        return null;
      }
      const normalized = {
        ...data,
        candidates: Array.isArray(data.candidates)
          ? data.candidates
          : buildStreamCandidates(data),
      };
      cache.set(job.key, { at: now(), data: normalized });
      return normalized;
    })
    .catch(() => {
      cache.set(job.key, { at: now(), data: null });
    })
    .finally(() => {
      window.clearTimeout(timer);
      active = Math.max(0, active - 1);
      inFlight.delete(job.key);
      pump();
    });
}

/**
 * Queues a background extraction. Safe to call for every visible card — the
 * queue is capped and only one browser extraction runs at a time.
 */
export function scheduleStreamPrefetch(params, { delay = 1400 } = {}) {
  const key = prefetchKey(params);
  if (!params?.mediaId || readEntry(key) || inFlight.has(key)) return () => {};
  if (queue.length >= MAX_QUEUE) return () => {};

  let cancelled = false;
  const idleHandle = idle(() => {
    if (cancelled || !isEnabled()) return;
    queue.push({ ...params, key });
    pump();
  }, delay);

  return () => {
    cancelled = true;
    if (typeof window.cancelIdleCallback === "function") {
      window.cancelIdleCallback(idleHandle);
    } else {
      window.clearTimeout(idleHandle);
    }
  };
}

/**
 * Observes a card and prefetches once it has been visible (and idle) for a
 * short moment. Returns the ref to attach to the card's root element.
 */
export function useStreamPrefetch(mediaId, mediaType = "movie", language = "") {
  const ref = useRef(null);
  const mediaTypeRef = useRef(mediaType);
  mediaTypeRef.current = mediaType;

  const cancelRef = useRef(null);
  useEffect(() => {
    const element = ref.current;
    if (
      !element ||
      !mediaId ||
      mediaType === "tv" ||
      typeof IntersectionObserver === "undefined"
    ) {
      return undefined;
    }

    let timer = null;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.some((entry) => entry.isIntersecting);
        if (visible) {
          if (timer) return;
          timer = window.setTimeout(() => {
            timer = null;
            cancelRef.current = scheduleStreamPrefetch({
              mediaId,
              mediaType: mediaTypeRef.current,
              language,
            });
          }, 1200);
        } else if (timer) {
          window.clearTimeout(timer);
          timer = null;
          cancelRef.current?.();
          cancelRef.current = null;
        }
      },
      { rootMargin: "180px" }
    );

    observer.observe(element);
    return () => {
      observer.disconnect();
      if (timer) window.clearTimeout(timer);
      cancelRef.current?.();
      cancelRef.current = null;
    };
  }, [language, mediaId, mediaType]);

  return ref;
}

/** Small helper for components that want a manual "prefetch on hover". */
export function useHoverPrefetch(params) {
  return useCallback(() => scheduleStreamPrefetch(params), [params]);
}
