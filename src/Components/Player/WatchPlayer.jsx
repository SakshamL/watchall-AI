import { useCallback, useEffect, useRef, useState } from "react";
import {
  ChevronLeft,
  Loader2,
  Maximize,
  Minimize,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Server,
  Settings,
  Subtitles,
  Sun,
  Volume1,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import PlayerSettings from "./PlayerSettings";
import PlayerTimeline from "./PlayerTimeline";
import {
  captionSizeKey,
  clamp,
  formatRuntime,
  formatTime,
  getDeviceProfile,
  qualityLabel,
  readStore,
  removeStore,
  resumeKey,
  serverDisplayName,
  writeStore,
} from "./playerUtils";
import { usePlaybackEngine } from "./usePlaybackEngine";
import "./player.css";

const TOP_EDGE_GUARD = 48;

const focusableIn = (root) => {
  if (!root) return [];
  return Array.from(root.querySelectorAll('[data-focusable="true"]')).filter((element) => {
    if (element.disabled || element.getAttribute("aria-hidden") === "true") return false;
    if (element.closest("[inert]")) return false;
    const rect = element.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) return false;
    const style = window.getComputedStyle(element);
    return (
      style.visibility !== "hidden" &&
      style.display !== "none" &&
      Number(style.opacity) > 0.05
    );
  });
};

function useDelayedFlag(value, delay = 260) {
  const [active, setActive] = useState(false);
  useEffect(() => {
    if (!value) {
      setActive(false);
      return undefined;
    }
    const timer = setTimeout(() => setActive(true), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return active;
}

const normalizeFatalError = (sourceError, snapshotError) => {
  if (sourceError) {
    if (typeof sourceError === "string") {
      return { title: "Unable to load the video", message: sourceError, retry: true };
    }
    return {
      title: sourceError.title || "Unable to load the video",
      message: sourceError.message || sourceError.error || "Try again in a moment.",
      retry: true,
    };
  }
  if (snapshotError) {
    return {
      title: snapshotError.title || "Playback error",
      message: snapshotError.message || "Try again or choose another server.",
      retry: true,
    };
  }
  return null;
};

export default function WatchPlayer({
  source,
  sourceLoading,
  sourceError,
  loadingMessage,
  metadata,
  originalLanguage = "",
  mediaId,
  initialTime = 0,
  servers = [],
  serversReady = false,
  activeServer,
  switchingServer,
  onSelectServer,
  onRetrySource,
  onTryOtherSource,
  otherSourceLabel = "",
  bothSourcesFailed = false,
  onBack,
  onFatalPlaybackError,
  notice,
}) {
  const [profile] = useState(() => getDeviceProfile());
  const engine = usePlaybackEngine({
    source,
    initialTime,
    profile,
    mediaId,
    originalLanguage,
    onFatalError: onFatalPlaybackError,
  });
  const {
    snapshot,
    videoRef,
    resumedFrom,
    clearResumeNotice,
    play,
    pause,
    togglePlay,
    seek,
    seekBy,
    setVolume,
    toggleMute,
    setPlaybackRate,
    setQuality,
    setAudioTrack,
    setTextTrack,
    toggleCaptions,
    goLive,
    retry,
  } = engine;

  const rootRef = useRef(null);
  const settingsRef = useRef(null);
  const helpRef = useRef(null);
  const playBtnRef = useRef(null);
  const centerPlayRef = useRef(null);
  const settingsBtnRef = useRef(null);
  const hideTimerRef = useRef(null);
  const flashTimerRef = useRef(null);
  const toastTimerRef = useRef(null);
  const tapTimerRef = useRef(null);
  const mouseTapTimerRef = useRef(null);
  const gestureTimerRef = useRef(null);
  const gestureRef = useRef(null);
  const loadingStartRef = useRef(0);
  const wasLoadingRef = useRef(false);
  const autoFullscreenTriedRef = useRef(false);
  const lastTapRef = useRef({ time: 0, side: "" });
  const settingsReturnFocusRef = useRef(null);
  const helpReturnFocusRef = useRef(null);
  const errorCardRef = useRef(null);
  const errorPrimaryRef = useRef(null);
  const tvFocusedRef = useRef(false);
  const brightnessRef = useRef(1);
  const uiVisibleRef = useRef(true);
  const settingsOpenRef = useRef(false);
  const helpOpenRef = useRef(false);
  const errorOpenRef = useRef(false);
  const isPlayingRef = useRef(false);

  const [uiVisible, setUiVisible] = useState(true);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [hoverChrome, setHoverChrome] = useState(false);
  const [captionSize, setCaptionSize] = useState(() =>
    readStore(captionSizeKey, "default")
  );
  const [toast, setToast] = useState(null);
  const [announcement, setAnnouncement] = useState({ id: 0, text: "" });
  const [seekFlash, setSeekFlash] = useState(null);
  const [gestureHud, setGestureHud] = useState(null);
  const [brightness, setBrightness] = useState(1);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [loadingElapsed, setLoadingElapsed] = useState(0);

  const isPlaying = snapshot.status === "playing";
  const isEnded = snapshot.status === "ended";
  const sourceFatal = typeof sourceError !== "undefined" && sourceError !== null;
  const fatalError =
    normalizeFatalError(sourceError, snapshot.error) ||
    (!source && !sourceLoading
      ? {
          title: "No stream available",
          message: "The stream could not be started. Please try again.",
          retry: true,
        }
      : null);
  const sourceFailure =
    sourceFatal || (!source && !sourceLoading);
  const errorVisible = Boolean(fatalError);
  const canTryOtherSource = Boolean(onTryOtherSource && otherSourceLabel);
  const preferOtherSource =
    sourceFailure && canTryOtherSource && !bothSourcesFailed;
  const tryOtherLabel = otherSourceLabel
    ? `Try ${otherSourceLabel}${bothSourcesFailed ? " again" : ""}`
    : "";
  const preparing = sourceLoading || (!source && !sourceFatal);
  const loadingVisible =
    !fatalError && (preparing || snapshot.status === "loading");
  const buffering = useDelayedFlag(snapshot.isBuffering, 250);
  const uiShown = uiVisible || !isPlaying || settingsOpen || helpOpen;
  const title = metadata?.title || "Now playing";
  const serverIsBusy = !!switchingServer;
  const hasCaptions = snapshot.textTracks.length > 0;
  const captionsOn = snapshot.textTrack >= 0;
  const liveWindowDuration = Math.max(
    0,
    snapshot.seekableEnd - snapshot.seekableStart
  );
  const timelineDuration = snapshot.isLive
    ? liveWindowDuration
    : snapshot.duration || 0;
  const timelineCurrent = snapshot.isLive
    ? Math.max(0, snapshot.currentTime - snapshot.seekableStart)
    : snapshot.currentTime;
  const timelineBuffered = snapshot.isLive
    ? Math.max(0, snapshot.bufferedEnd - snapshot.seekableStart)
    : snapshot.bufferedEnd;
  const atLiveEdge =
    snapshot.isLive &&
    snapshot.seekableEnd > 0 &&
    snapshot.seekableEnd - snapshot.currentTime < 7;
  const showCenterPlay =
    !loadingVisible &&
    !fatalError &&
    !isEnded &&
    (snapshot.status === "paused" ||
      snapshot.status === "ready" ||
      snapshot.status === "idle" ||
      snapshot.autoplayBlocked);

  useEffect(() => {
    if (!loadingVisible) {
      wasLoadingRef.current = false;
      setLoadingElapsed(0);
      return undefined;
    }
    if (!wasLoadingRef.current) {
      wasLoadingRef.current = true;
      loadingStartRef.current = Date.now();
      setLoadingElapsed(0);
    }
    const tick = () =>
      setLoadingElapsed(
        Math.max(0, Math.floor((Date.now() - loadingStartRef.current) / 1000))
      );
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [loadingVisible]);

  const loadingPhase =
    loadingElapsed >= 40
      ? "Finishing up"
      : loadingElapsed >= 20
        ? "Verifying playback"
        : loadingElapsed >= 8
          ? "Finding the best stream"
          : "Checking available servers";

  uiVisibleRef.current = uiVisible;
  settingsOpenRef.current = settingsOpen;
  helpOpenRef.current = helpOpen;
  errorOpenRef.current = errorVisible;
  isPlayingRef.current = isPlaying;
  brightnessRef.current = brightness;

  const announce = useCallback((text) => {
    setAnnouncement({ id: Date.now(), text });
  }, []);

  const showToast = useCallback((message, options = {}) => {
    clearTimeout(toastTimerRef.current);
    setToast({
      id: Date.now(),
      message,
      tone: options.tone || "default",
      actionLabel: options.actionLabel || "",
      onAction: options.onAction || null,
    });
    if (options.duration !== 0) {
      toastTimerRef.current = setTimeout(
        () => setToast(null),
        options.duration || 4200
      );
    }
  }, []);

  const clearHideTimer = useCallback(() => {
    clearTimeout(hideTimerRef.current);
    hideTimerRef.current = null;
  }, []);

  const armHideTimer = useCallback(() => {
    clearHideTimer();
    if (!isPlaying || settingsOpen || helpOpen || hoverChrome) return;
    hideTimerRef.current = setTimeout(
      () => setUiVisible(false),
      profile.isTV ? 5000 : 3000
    );
  }, [
    clearHideTimer,
    hoverChrome,
    helpOpen,
    isPlaying,
    profile.isTV,
    settingsOpen,
  ]);

  const showUi = useCallback(() => {
    setUiVisible(true);
    armHideTimer();
  }, [armHideTimer]);

  const hideUi = useCallback(() => {
    clearHideTimer();
    setUiVisible(false);
  }, [clearHideTimer]);

  useEffect(() => {
    if (settingsOpen || helpOpen) {
      clearHideTimer();
      setUiVisible(true);
      return undefined;
    }
    if (isPlaying) armHideTimer();
    else {
      clearHideTimer();
      setUiVisible(true);
    }
    return clearHideTimer;
  }, [
    armHideTimer,
    clearHideTimer,
    helpOpen,
    hoverChrome,
    isPlaying,
    settingsOpen,
  ]);

  useEffect(
    () => () => {
      clearTimeout(hideTimerRef.current);
      clearTimeout(flashTimerRef.current);
      clearTimeout(toastTimerRef.current);
      clearTimeout(tapTimerRef.current);
      clearTimeout(mouseTapTimerRef.current);
      clearTimeout(gestureTimerRef.current);
    },
    []
  );

  const handleActivity = useCallback((event) => {
    // Touch and pen on the video surface are handled by the gesture layer.
    // Showing the chrome on every touch move made swipes for brightness and
    // volume reveal the controls. Touches on the controls themselves still
    // count as activity so the chrome does not hide mid-drag.
    const isPointer =
      event?.type === "pointermove" || event?.type === "pointerdown";
    const onVideoSurface =
      isPointer &&
      event.pointerType !== "mouse" &&
      event.target?.closest?.(".wa-gesture-layer");
    if (onVideoSurface) return;
    if (!uiVisibleRef.current) setUiVisible(true);
    armHideTimer();
  }, [armHideTimer]);

  const flashSeek = useCallback(
    (seconds) => {
      seekBy(seconds);
      setSeekFlash({ id: Date.now(), seconds });
      announce(
        `${Math.abs(seconds)} seconds ${seconds > 0 ? "forward" : "back"}`
      );
      showUi();
      clearTimeout(flashTimerRef.current);
      flashTimerRef.current = setTimeout(() => setSeekFlash(null), 700);
    },
    [announce, seekBy, showUi]
  );

  const handleTogglePlay = useCallback(() => {
    const video = videoRef.current;
    const willPlay = !!video?.paused || video?.ended;
    togglePlay();
    announce(willPlay ? "Playing" : "Paused");
    showUi();
  }, [announce, showUi, togglePlay, videoRef]);

  const handleToggleMute = useCallback(() => {
    const willMute = !videoRef.current?.muted;
    toggleMute();
    announce(willMute ? "Muted" : "Sound on");
    showUi();
  }, [announce, showUi, toggleMute, videoRef]);

  const handleToggleCaptions = useCallback(() => {
    const willTurnOn = snapshot.textTrack < 0;
    toggleCaptions();
    announce(willTurnOn ? "Subtitles on" : "Subtitles off");
    showUi();
  }, [announce, showUi, snapshot.textTrack, toggleCaptions]);

  const handleRetryAction = useCallback(() => {
    if (serverIsBusy) return;
    if (sourceFailure && onRetrySource) onRetrySource();
    else retry();
  }, [onRetrySource, retry, serverIsBusy, sourceFailure]);

  const handleTryOtherSource = useCallback(() => {
    if (serverIsBusy || !onTryOtherSource) return;
    onTryOtherSource();
  }, [onTryOtherSource, serverIsBusy]);

  const handleSeek = useCallback(
    (value) => {
      seek(value);
      announce(`Seeked to ${formatTime(value)}`);
      showUi();
    },
    [announce, seek, showUi]
  );

  const handleTimelineSeek = useCallback(
    (value) => {
      const target = (snapshot.isLive ? snapshot.seekableStart : 0) + value;
      seek(target);
      announce(`Seeked to ${formatTime(target)}`);
      showUi();
    },
    [announce, seek, showUi, snapshot.isLive, snapshot.seekableStart]
  );

  const openSettings = useCallback(() => {
    settingsReturnFocusRef.current = document.activeElement;
    setSettingsOpen(true);
    setUiVisible(true);
    clearHideTimer();
  }, [clearHideTimer]);

  const closeSettings = useCallback(() => {
    setSettingsOpen(false);
    const target = settingsReturnFocusRef.current;
    setTimeout(() => {
      if (target && document.contains(target)) {
        target.focus({ preventScroll: true });
      } else {
        settingsBtnRef.current?.focus({ preventScroll: true });
      }
    }, 0);
  }, []);

  const openHelp = useCallback(() => {
    helpReturnFocusRef.current =
      settingsOpenRef.current && settingsRef.current
        ? settingsRef.current.querySelector('[data-focusable="true"]')
        : document.activeElement;
    setSettingsOpen(false);
    setHelpOpen(true);
    setUiVisible(true);
    clearHideTimer();
  }, [clearHideTimer]);

  const closeHelp = useCallback(() => {
    setHelpOpen(false);
    const target = helpReturnFocusRef.current;
    setTimeout(() => {
      if (target && document.contains(target)) {
        target.focus({ preventScroll: true });
      } else {
        settingsBtnRef.current?.focus({ preventScroll: true });
      }
    }, 0);
  }, []);

  const handleSettingsAction = useCallback(
    (action, rawValue) => {
      showUi();
      if (action === "quality") {
        const value = Number(rawValue);
        setQuality(value);
        const label =
          value === -1
            ? "Auto"
            : qualityLabel(snapshot.qualities.find((item) => item.id === value));
        announce(`Quality: ${label}`);
      } else if (action === "rate") {
        const value = Number(rawValue);
        setPlaybackRate(value);
        announce(`Playback speed ${value} times`);
      } else if (action === "audio") {
        const value = Number(rawValue);
        const track = snapshot.audioTracks[value];
        setAudioTrack(value);
        announce(`Audio: ${track?.label || track?.language || value + 1}`);
      } else if (action === "text") {
        const value = Number(rawValue);
        setTextTrack(value);
        const track = snapshot.textTracks[value];
        announce(
          value < 0
            ? "Subtitles off"
            : `Subtitles: ${track?.label || track?.language || value + 1}`
        );
      } else if (action === "caption-size") {
        setCaptionSize(rawValue);
        writeStore(captionSizeKey, rawValue);
      }
    },
    [
      announce,
      setAudioTrack,
      setPlaybackRate,
      setQuality,
      setTextTrack,
      showUi,
      snapshot,
    ]
  );

  const enterPlayerFullscreen = useCallback(
    async (silent = false) => {
      const root = rootRef.current;
      const video = videoRef.current;
      try {
        if (document.fullscreenElement || document.webkitFullscreenElement) {
          return true;
        }
        if (profile.isIOS && video?.webkitEnterFullscreen) {
          video.webkitEnterFullscreen();
          return true;
        }
        if (root?.requestFullscreen) {
          await root.requestFullscreen();
          return true;
        }
        if (root?.webkitRequestFullscreen) {
          root.webkitRequestFullscreen();
          return true;
        }
        if (video?.webkitEnterFullscreen) {
          video.webkitEnterFullscreen();
          return true;
        }
        if (!silent) showToast("Fullscreen is not supported on this device.");
        return false;
      } catch {
        if (!silent) showToast("Fullscreen is not available right now.");
        return false;
      }
    },
    [profile.isIOS, showToast, videoRef]
  );

  const toggleFullscreen = useCallback(async () => {
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      try {
        if (document.exitFullscreen) await document.exitFullscreen();
        else document.webkitExitFullscreen?.();
      } catch {
        showToast("Could not exit fullscreen right now.");
      }
      return;
    }
    await enterPlayerFullscreen(false);
  }, [enterPlayerFullscreen, showToast]);

  useEffect(() => {
    if (!profile.isTV || autoFullscreenTriedRef.current) return undefined;
    if (snapshot.status !== "playing") return undefined;
    let active = true;
    const cleanup = () => {
      window.removeEventListener("pointerdown", attempt, true);
      window.removeEventListener("keydown", attempt, true);
    };
    const attempt = () => {
      if (!active || autoFullscreenTriedRef.current) return;
      if (document.fullscreenElement || document.webkitFullscreenElement) {
        autoFullscreenTriedRef.current = true;
        cleanup();
        return;
      }
      enterPlayerFullscreen(true).then((entered) => {
        if (entered) {
          autoFullscreenTriedRef.current = true;
          cleanup();
        }
      });
    };
    const timer = window.setTimeout(attempt, 260);
    window.addEventListener("pointerdown", attempt, true);
    window.addEventListener("keydown", attempt, true);
    return () => {
      active = false;
      window.clearTimeout(timer);
      cleanup();
    };
  }, [enterPlayerFullscreen, profile.isTV, snapshot.status]);

  useEffect(() => {
    const sync = () =>
      setIsFullscreen(
        !!(document.fullscreenElement || document.webkitFullscreenElement)
      );
    const video = videoRef.current;
    const begin = () => setIsFullscreen(true);
    const end = () => setIsFullscreen(false);
    document.addEventListener("fullscreenchange", sync);
    document.addEventListener("webkitfullscreenchange", sync);
    video?.addEventListener("webkitbeginfullscreen", begin);
    video?.addEventListener("webkitendfullscreen", end);
    return () => {
      document.removeEventListener("fullscreenchange", sync);
      document.removeEventListener("webkitfullscreenchange", sync);
      video?.removeEventListener("webkitbeginfullscreen", begin);
      video?.removeEventListener("webkitendfullscreen", end);
    };
  }, [videoRef]);

  useEffect(() => {
    if (!isFullscreen || !profile.isMobile) return undefined;
    screen.orientation?.lock?.("landscape").catch(() => {});
    return () => {
      screen.orientation?.unlock?.();
    };
  }, [isFullscreen, profile.isMobile]);

  useEffect(() => {
    if (!resumedFrom || !mediaId) return;
    showToast(`Resumed from ${formatTime(resumedFrom)}`, {
      actionLabel: "Start over",
      duration: 7500,
      onAction: () => {
        seek(0);
        removeStore(resumeKey(mediaId));
        clearResumeNotice();
      },
    });
  }, [clearResumeNotice, mediaId, resumedFrom, seek, showToast]);

  useEffect(() => {
    if (!notice) return;
    const text = typeof notice === "string" ? notice : notice.text;
    if (!text) return;
    showToast(text, {
      tone: typeof notice === "object" ? notice.tone : "default",
      duration: typeof notice === "object" ? notice.duration : undefined,
    });
  }, [notice, showToast]);

  useEffect(() => {
    if (!mediaId) return undefined;
    const video = videoRef.current;
    if (!video) return undefined;
    const key = resumeKey(mediaId);
    const save = () => {
      const duration = video.duration;
      if (!Number.isFinite(duration) || duration <= 0) return;
      const current = video.currentTime;
      if (video.ended || current >= duration - 10) {
        removeStore(key);
        return;
      }
      if (current > 5) {
        writeStore(key, { position: current, updated: Date.now() });
      }
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "hidden") save();
    };
    const onEnded = () => removeStore(key);
    const interval = setInterval(save, 5000);
    window.addEventListener("pagehide", save);
    document.addEventListener("visibilitychange", onVisibilityChange);
    video.addEventListener("pause", save);
    video.addEventListener("ended", onEnded);
    return () => {
      clearInterval(interval);
      window.removeEventListener("pagehide", save);
      document.removeEventListener("visibilitychange", onVisibilityChange);
      video.removeEventListener("pause", save);
      video.removeEventListener("ended", onEnded);
      save();
    };
  }, [mediaId, videoRef]);

  useEffect(() => {
    if (!("mediaSession" in navigator) || !("MediaMetadata" in window)) {
      return undefined;
    }
    const artwork = metadata?.poster
      ? [
          { src: metadata.poster, sizes: "512x768", type: "image/jpeg" },
        ]
      : [];
    navigator.mediaSession.metadata = new window.MediaMetadata({
      title: metadata?.title || "WatchAll",
      artist: metadata?.subtitle || "WatchAll",
      album: metadata?.year ? `${metadata.year}` : "WatchAll",
      artwork,
    });
    return undefined;
  }, [metadata]);

  useEffect(() => {
    if (!("mediaSession" in navigator)) return undefined;
    const session = navigator.mediaSession;
    const handlers = {
      play: () => play(),
      pause: () => pause(),
      seekbackward: (details) => seekBy(-(details?.seekOffset || 10)),
      seekforward: (details) => seekBy(details?.seekOffset || 10),
      seekto: (details) => {
        if (typeof details?.seekTime === "number") seek(details.seekTime);
      },
      stop: () => pause(),
    };
    Object.entries(handlers).forEach(([action, handler]) => {
      try {
        session.setActionHandler(action, handler);
      } catch (error) {
        void error;
      }
    });
    return () => {
      Object.keys(handlers).forEach((action) => {
        try {
          session.setActionHandler(action, null);
        } catch (error) {
          void error;
        }
      });
    };
  }, [pause, play, seek, seekBy]);

  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.playbackState = isPlaying ? "playing" : "paused";
  }, [isPlaying]);

  const positionSecond = Math.floor(snapshot.currentTime);
  useEffect(() => {
    if (!("mediaSession" in navigator) || !navigator.mediaSession.setPositionState) {
      return;
    }
    if (
      snapshot.isLive ||
      !Number.isFinite(snapshot.duration) ||
      snapshot.duration <= 0
    ) {
      return;
    }
    try {
      navigator.mediaSession.setPositionState({
        duration: snapshot.duration,
        playbackRate: snapshot.playbackRate,
        position: clamp(snapshot.currentTime, 0, snapshot.duration),
      });
    } catch (error) {
      void error;
    }
  }, [
    positionSecond,
    snapshot.currentTime,
    snapshot.duration,
    snapshot.isLive,
    snapshot.playbackRate,
  ]);

  useEffect(() => {
    if (!isPlaying || !("wakeLock" in navigator)) return undefined;
    let lock = null;
    let cancelled = false;
    const acquire = async () => {
      try {
        lock = await navigator.wakeLock.request("screen");
        if (cancelled) {
          await lock.release();
          lock = null;
        }
      } catch (error) {
        void error;
      }
    };
    const onVisibility = () => {
      if (document.visibilityState === "visible" && !cancelled) acquire();
    };
    acquire();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", onVisibility);
      try {
        lock?.release();
      } catch (error) {
        void error;
      }
    };
  }, [isPlaying]);

  useEffect(() => {
    if (!profile.isTV || tvFocusedRef.current) return undefined;
    if (snapshot.status !== "playing" && snapshot.status !== "paused") {
      return undefined;
    }
    tvFocusedRef.current = true;
    const timer = setTimeout(() => {
      const target = snapshot.autoplayBlocked
        ? centerPlayRef.current
        : playBtnRef.current;
      target?.focus({ preventScroll: true });
    }, 320);
    return () => clearTimeout(timer);
  }, [profile.isTV, snapshot.autoplayBlocked, snapshot.status]);

  useEffect(() => {
    if (!settingsOpen) return undefined;
    const container = settingsRef.current;
    if (!container) return undefined;
    const initial =
      container.querySelector('[data-action="quality"].is-active') ||
      focusableIn(container)[0];
    initial?.focus({ preventScroll: true });
    const trap = (event) => {
      if (event.key !== "Tab") return;
      const nodes = focusableIn(container);
      if (!nodes.length) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (
        event.shiftKey &&
        (document.activeElement === first ||
          !container.contains(document.activeElement))
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last ||
          !container.contains(document.activeElement))
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", trap, true);
    return () => document.removeEventListener("keydown", trap, true);
  }, [settingsOpen]);

  useEffect(() => {
    if (!helpOpen) return undefined;
    const container = helpRef.current;
    if (!container) return undefined;
    focusableIn(container)[0]?.focus({ preventScroll: true });
    const trap = (event) => {
      if (event.key !== "Tab") return;
      const nodes = focusableIn(container);
      if (!nodes.length) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (
        event.shiftKey &&
        (document.activeElement === first ||
          !container.contains(document.activeElement))
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last ||
          !container.contains(document.activeElement))
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", trap, true);
    return () => document.removeEventListener("keydown", trap, true);
  }, [helpOpen]);

  useEffect(() => {
    if (!errorVisible) return;
    setSettingsOpen(false);
    setHelpOpen(false);
  }, [errorVisible]);

  useEffect(() => {
    if (!errorVisible || settingsOpen || helpOpen || serverIsBusy) {
      return undefined;
    }
    const frame = requestAnimationFrame(() => {
      errorPrimaryRef.current?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [errorVisible, helpOpen, serverIsBusy, settingsOpen]);

  useEffect(() => {
    if (!errorVisible || settingsOpen || helpOpen) return undefined;
    const container = errorCardRef.current;
    if (!container) return undefined;
    const trap = (event) => {
      if (event.key !== "Tab") return;
      const nodes = focusableIn(container);
      if (!nodes.length) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      if (
        event.shiftKey &&
        (document.activeElement === first ||
          !container.contains(document.activeElement))
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last ||
          !container.contains(document.activeElement))
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", trap, true);
    return () => document.removeEventListener("keydown", trap, true);
  }, [errorVisible, helpOpen, settingsOpen]);

  const moveFocus = useCallback((direction) => {
    const container = settingsOpenRef.current
      ? settingsRef.current
      : errorOpenRef.current
        ? errorCardRef.current
        : rootRef.current;
    const nodes = focusableIn(container);
    if (!nodes.length) return false;
    const active = document.activeElement;
    if (!container?.contains(active) || !nodes.includes(active)) {
      const fallback =
        settingsOpenRef.current || errorOpenRef.current
          ? nodes[0]
          : playBtnRef.current || nodes[0];
      fallback?.focus({ preventScroll: true });
      return true;
    }
    if (errorOpenRef.current && !settingsOpenRef.current) {
      const step = direction === "down" || direction === "right" ? 1 : -1;
      const index = nodes.indexOf(active);
      const next = nodes[Math.max(0, Math.min(nodes.length - 1, index + step))];
      if (next) {
        next.focus({ preventScroll: true });
        next.scrollIntoView?.({ block: "nearest", inline: "nearest" });
      }
      return true;
    }
    const rect = active.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const horizontal = direction === "left" || direction === "right";
    let best = null;
    let bestScore = Number.POSITIVE_INFINITY;
    nodes.forEach((node) => {
      if (node === active) return;
      const nodeRect = node.getBoundingClientRect();
      const dx = nodeRect.left + nodeRect.width / 2 - centerX;
      const dy = nodeRect.top + nodeRect.height / 2 - centerY;
      if (direction === "left" && dx > -6) return;
      if (direction === "right" && dx < 6) return;
      if (direction === "up" && dy > -6) return;
      if (direction === "down" && dy < 6) return;
      const primary = horizontal ? Math.abs(dx) : Math.abs(dy);
      const secondary = horizontal ? Math.abs(dy) : Math.abs(dx);
      if (primary < 4) return;
      if (secondary > primary * 2.4) return;
      const score = primary + secondary * 1.6;
      if (score < bestScore) {
        bestScore = score;
        best = node;
      }
    });
    if (best) {
      best.focus({ preventScroll: true });
      best.scrollIntoView?.({ block: "nearest", inline: "nearest" });
      return true;
    }
    return false;
  }, []);

  const actionsRef = useRef({});
  actionsRef.current = {
    togglePlay: handleTogglePlay,
    pause,
    seekBy: flashSeek,
    setVolume,
    seek: handleSeek,
    showUi,
    hideUi,
    openSettings,
    closeSettings,
    openHelp,
    closeHelp,
    toggleFullscreen,
    toggleMute: handleToggleMute,
    toggleCaptions: handleToggleCaptions,
    back: onBack,
    announce,
  };

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.defaultPrevented) return;
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      const actions = actionsRef.current;
      const key = event.key;
      const code = event.keyCode;
      const active = document.activeElement;
      const tag = active?.tagName;
      const isInput =
        tag === "INPUT" ||
        tag === "TEXTAREA" ||
        tag === "SELECT" ||
        !!active?.isContentEditable;
      const isButton =
        tag === "BUTTON" ||
        tag === "A" ||
        active?.getAttribute?.("role") === "slider";
      const isSlider = active?.getAttribute?.("role") === "slider";
      const isArrow =
        key === "ArrowLeft" ||
        key === "ArrowRight" ||
        key === "ArrowUp" ||
        key === "ArrowDown";
      const isBack =
        key === "Escape" ||
        key === "Backspace" ||
        key === "GoBack" ||
        key === "BrowserBack" ||
        code === 461 ||
        code === 10009;

      if (helpOpenRef.current) {
        if (isBack) {
          event.preventDefault();
          actions.closeHelp();
        }
        return;
      }

      if (settingsOpenRef.current) {
        if (isBack) {
          event.preventDefault();
          actions.closeSettings();
          return;
        }
        if (isArrow && !isInput) {
          const direction = key.replace("Arrow", "").toLowerCase();
          if (moveFocus(direction)) {
            event.preventDefault();
            actions.showUi();
          }
          return;
        }
        if (
          key === "MediaPlayPause" ||
          code === 179 ||
          code === 415 ||
          key === "Play"
        ) {
          event.preventDefault();
          actions.togglePlay();
        } else if (code === 19 || key === "Pause") {
          event.preventDefault();
          actions.pause();
        }
        return;
      }

      if (errorOpenRef.current) {
        if (isBack) {
          event.preventDefault();
          actions.back();
          return;
        }
        if (isArrow && !isInput) {
          event.preventDefault();
          moveFocus(key.replace("Arrow", "").toLowerCase());
        }
        return;
      }

      if (isBack) {
        event.preventDefault();
        if (uiVisibleRef.current) actions.hideUi();
        else actions.back();
        return;
      }

      if (
        key === "MediaPlayPause" ||
        code === 179 ||
        code === 415 ||
        key === "Play"
      ) {
        event.preventDefault();
        actions.togglePlay();
        return;
      }
      if (code === 19 || key === "Pause") {
        event.preventDefault();
        actions.pause();
        return;
      }
      if (key === "MediaStop" || code === 413) {
        event.preventDefault();
        actions.pause();
        return;
      }
      if (key === "MediaFastForward" || code === 417) {
        event.preventDefault();
        actions.seekBy(10);
        return;
      }
      if (key === "MediaRewind" || code === 412) {
        event.preventDefault();
        actions.seekBy(-10);
        return;
      }

      if (isArrow) {
        if (isInput) return;
        event.preventDefault();
        const direction = key.replace("Arrow", "").toLowerCase();
        if (profile.isTV) {
          if (!uiVisibleRef.current) {
            actions.showUi();
            if (direction === "left" || direction === "right") {
              actions.seekBy(direction === "left" ? -10 : 10);
            } else {
              requestAnimationFrame(() =>
                playBtnRef.current?.focus({ preventScroll: true })
              );
            }
            return;
          }
          moveFocus(direction);
        } else {
          actions.showUi();
          if (direction === "left" || direction === "right") {
            actions.seekBy(direction === "left" ? -10 : 10);
          } else {
            const video = videoRef.current;
            const current = Number(video?.volume ?? 1);
            actions.setVolume(
              clamp(current + (direction === "up" ? 0.05 : -0.05), 0, 1)
            );
          }
        }
        return;
      }

      if (key === "Enter") {
        if (isButton) return;
        event.preventDefault();
        if (profile.isTV) {
          if (!uiVisibleRef.current) {
            actions.showUi();
            requestAnimationFrame(() =>
              playBtnRef.current?.focus({ preventScroll: true })
            );
          } else {
            actions.togglePlay();
          }
        } else {
          actions.togglePlay();
        }
        return;
      }

      if (key === " " || key === "Spacebar") {
        if (isInput) return;
        if (isSlider) {
          event.preventDefault();
          actions.togglePlay();
          return;
        }
        if (isButton) return;
        event.preventDefault();
        actions.togglePlay();
        return;
      }

      if (isInput) return;

      if (key === "k" || key === "K") {
        event.preventDefault();
        actions.togglePlay();
      } else if (key === "j" || key === "J") {
        event.preventDefault();
        actions.seekBy(-10);
      } else if (key === "l" || key === "L") {
        event.preventDefault();
        actions.seekBy(10);
      } else if (key === "m" || key === "M") {
        if (!event.repeat) {
          event.preventDefault();
          actions.toggleMute();
        }
      } else if (key === "c" || key === "C") {
        if (!event.repeat) {
          event.preventDefault();
          actions.toggleCaptions();
        }
      } else if (key === "f" || key === "F") {
        if (!event.repeat) {
          event.preventDefault();
          actions.toggleFullscreen();
        }
      } else if (key === "?" || (key === "/" && event.shiftKey)) {
        event.preventDefault();
        actions.openHelp();
      } else if (/^[0-9]$/.test(key)) {
        const video = videoRef.current;
        const duration =
          Number.isFinite(video?.duration) && video.duration > 0
            ? video.duration
            : video?.seekable?.length
              ? video.seekable.end(video.seekable.length - 1)
              : 0;
        if (duration > 0) {
          event.preventDefault();
          actions.seek((duration * Number(key)) / 10);
        }
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [moveFocus, profile.isTV, videoRef]);

  const handleGesturePointerDown = (event) => {
    // Remember whether the controls were already visible before this tap.
    // For touch, the chrome is shown on tap-up instead of touch-down so that
    // swipes never reveal the controls.
    const startedUiVisible = uiVisibleRef.current;
    if (event.pointerType === "mouse") {
      showUi();
      return;
    }
    const style = window.getComputedStyle(event.currentTarget);
    const safeTop = Number.parseFloat(style.getPropertyValue("--wa-safe-top")) || 0;
    const topGuard = Math.max(TOP_EDGE_GUARD, safeTop + 16);
    if (event.clientY <= topGuard) {
      // Leave the top edge alone so the device can reveal its status bar or
      // notification shade. Do not capture this pointer.
      gestureRef.current = {
        pointerId: event.pointerId,
        ignored: true,
        startedUiVisible,
      };
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    gestureRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startVolume: videoRef.current?.volume ?? 1,
      startBrightness: brightnessRef.current,
      centerX: rect.left + rect.width / 2,
      height: rect.height || window.innerHeight,
      startedUiVisible,
      mode: "",
      moved: false,
    };
    gestureRef.current.element = event.currentTarget;
    try {
      event.currentTarget.setPointerCapture?.(event.pointerId);
    } catch {
      // Synthetic events and some TV browsers do not support pointer capture.
    }
  };

  const handleGesturePointerMove = (event) => {
    const gesture = gestureRef.current;
    if (!gesture || gesture.pointerId !== event.pointerId || gesture.ignored) return;
    const dx = event.clientX - gesture.startX;
    const dy = event.clientY - gesture.startY;
    // Any real movement means this is a swipe, not a tap. Without this, a
    // horizontal swipe was treated as a tap and toggled the controls.
    if (!gesture.moved && Math.hypot(dx, dy) > 12) {
      gesture.moved = true;
    }
    if (!gesture.mode && Math.abs(dy) > 18 && Math.abs(dy) > Math.abs(dx)) {
      gesture.mode =
        gesture.startX < gesture.centerX
          ? "brightness"
          : profile.isIOS
            ? "volume-hint"
            : "volume";
    }
    if (!gesture.mode) return;
    event.preventDefault();
    const delta = -dy / Math.max(180, gesture.height * 0.55);
    clearTimeout(gestureTimerRef.current);
    // Safety net: if the release event is ever lost, the HUD still clears.
    gestureTimerRef.current = setTimeout(() => setGestureHud(null), 2500);
    if (gesture.mode === "brightness") {
      const next = clamp(gesture.startBrightness + delta, 0.35, 1.15);
      setBrightness(next);
      setGestureHud({
        id: Date.now(),
        kind: "brightness",
        value: (next - 0.35) / 0.8,
      });
    } else if (gesture.mode === "volume") {
      const next = clamp(gesture.startVolume + delta, 0, 1);
      setVolume(next);
      setGestureHud({ id: Date.now(), kind: "volume", value: next });
    } else {
      setGestureHud({ id: Date.now(), kind: "volume-hint", value: 0 });
    }
  };

  const handleGesturePointerUp = (event) => {
    const gesture = gestureRef.current;
    gestureRef.current = null;
    if (event.pointerType === "mouse") {
      // Delay the single-click play toggle briefly so a double-click can
      // become fullscreen without play/pause flickering twice.
      clearTimeout(mouseTapTimerRef.current);
      mouseTapTimerRef.current = window.setTimeout(() => {
        mouseTapTimerRef.current = null;
        handleTogglePlay();
      }, 230);
      return;
    }
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    if (gesture.ignored) return;
    try {
      event.currentTarget.releasePointerCapture?.(event.pointerId);
    } catch {
      // Ignore capture released by the browser or unsupported capture.
    }
    if (gesture.mode) {
      clearTimeout(gestureTimerRef.current);
      gestureTimerRef.current = setTimeout(() => setGestureHud(null), 650);
      return;
    }
    if (gesture.moved) return;
    const now = Date.now();
    const side =
      event.clientX < window.innerWidth * 0.35
        ? "left"
        : event.clientX > window.innerWidth * 0.65
          ? "right"
          : "center";
    const last = lastTapRef.current;
    if (now - last.time < 340 && last.side === side && side !== "center") {
      clearTimeout(tapTimerRef.current);
      lastTapRef.current = { time: 0, side: "" };
      flashSeek(side === "left" ? -10 : 10);
      return;
    }
    lastTapRef.current = { time: now, side };
    // A tap on a hidden player should reveal the chrome immediately.
    if (!gesture?.startedUiVisible) showUi();
    clearTimeout(tapTimerRef.current);
    tapTimerRef.current = setTimeout(() => {
      // Only hide when the controls were already on screen before this tap.
      // A tap on a hidden player keeps them visible for the normal timeout.
      if (gesture?.startedUiVisible && isPlayingRef.current) hideUi();
      else showUi();
    }, 280);
  };

  const handleGesturePointerCancel = () => {
    const gesture = gestureRef.current;
    gestureRef.current = null;
    // The browser can cancel a touch pointer (for example when it takes over
    // scrolling). Clear the HUD just like a normal release so it never sticks.
    if (gesture?.mode) {
      clearTimeout(gestureTimerRef.current);
      gestureTimerRef.current = setTimeout(() => setGestureHud(null), 650);
    }
  };

  const handleGestureDoubleClick = (event) => {
    if (!profile.isDesktop) return;
    event.preventDefault();
    clearTimeout(mouseTapTimerRef.current);
    mouseTapTimerRef.current = null;
    toggleFullscreen();
  };

  const volumeIcon =
    snapshot.muted || snapshot.volume === 0 ? (
      <VolumeX size={22} />
    ) : snapshot.volume < 0.5 ? (
      <Volume1 size={22} />
    ) : (
      <Volume2 size={22} />
    );

  const metaParts = [
    metadata?.year,
    formatRuntime(metadata?.runtime),
    metadata?.rating ? `★ ${metadata.rating}` : "",
  ].filter(Boolean);

  const shortcuts = [
    ["Space / K", "Play or pause"],
    ["← / →", "Seek 10 seconds"],
    ["J / L", "Seek 10 seconds"],
    ["↑ / ↓", "Volume"],
    ["M", "Mute or unmute"],
    ["F", "Fullscreen"],
    ["C", "Subtitles"],
    ["0–9", "Jump to percentage"],
    ["?", "Show shortcuts"],
    ["Esc / Back", "Close menu or hide controls"],
  ];

  return (
    <div
      ref={rootRef}
      className="wa-player"
      data-device={
        profile.isTV
          ? "tv"
          : profile.isMobile
            ? "mobile"
            : profile.isTablet
              ? "tablet"
              : "desktop"
      }
      data-low-power={profile.lowPower ? "true" : "false"}
      data-touch={profile.isTouch ? "true" : "false"}
      data-ios={profile.isIOS ? "true" : "false"}
      data-ui={uiShown ? "visible" : "hidden"}
      data-caption-size={captionSize}
      data-fullscreen={isFullscreen ? "true" : "false"}
      onPointerMove={handleActivity}
      onPointerDown={handleActivity}
      onFocusCapture={handleActivity}
    >
      <video
        ref={videoRef}
        className="wa-video"
        playsInline
        disablePictureInPicture
        preload="metadata"
        tabIndex={-1}
        aria-label={metadata?.title ? `Video player for ${metadata.title}` : "Video player"}
        style={brightness !== 1 ? { filter: `brightness(${brightness})` } : undefined}
      />

      <div
        className="wa-gesture-layer"
        aria-hidden="true"
        onPointerDown={handleGesturePointerDown}
        onPointerMove={handleGesturePointerMove}
        onPointerUp={handleGesturePointerUp}
        onPointerCancel={handleGesturePointerCancel}
        onDoubleClick={handleGestureDoubleClick}
      />

      <div className="wa-edge-guard" aria-hidden="true" />

      <div className="wa-scrim wa-scrim-top" />
      <div className="wa-scrim wa-scrim-bottom" />

      <div className="wa-topbar wa-chrome">
        <button
          type="button"
          className="wa-icon-btn wa-back-btn"
          onClick={onBack}
          aria-label="Go back"
          title="Go back"
          data-focusable="true"
        >
          <ChevronLeft size={24} />
        </button>
        <div className="wa-title-block">
          <h1 className="wa-title">{title}</h1>
          <div className="wa-meta">
            {metaParts.map((part, index) => (
              <span key={`${part}-${index}`} className="wa-meta-item">
                {part}
              </span>
            ))}
            {snapshot.isLive ? (
              <span className="wa-live-badge">
                <span className="wa-live-dot" /> LIVE
              </span>
            ) : null}
            {captionsOn ? <span className="wa-cc-badge">CC</span> : null}
          </div>
        </div>
        <button
          type="button"
          className="wa-server-chip"
          onClick={openSettings}
          aria-label={
            activeServer
              ? `Stream server: ${serverDisplayName(activeServer)}`
              : "Choose stream server"
          }
          title="Stream server"
          data-focusable="true"
        >
          {serverIsBusy ? (
            <Loader2 size={16} className="wa-spin" />
          ) : (
            <Server size={16} />
          )}
          <span className="wa-server-chip-label">
            {serverIsBusy
              ? `Switching to ${serverDisplayName(switchingServer)}…`
              : serverDisplayName(activeServer) || "Servers"}
          </span>
        </button>
      </div>

      {loadingVisible ? (
        <div className="wa-state wa-loading" role="status" aria-live="polite">
          {metadata?.backdrop ? (
            <img
              className="wa-loading-backdrop"
              src={metadata.backdrop}
              alt=""
              aria-hidden="true"
            />
          ) : null}
          <div className="wa-loading-scrim" />
          <div className="wa-loading-content">
            <h2>{title}</h2>
            <p className="wa-loading-message">
              {loadingMessage || "Getting your stream ready…"}
            </p>
            <div className="wa-loading-meter" aria-hidden="true">
              <span />
            </div>
            <div className="wa-loading-status" aria-live="off">
              <span className="wa-loading-phase">{loadingPhase}</span>
              <span className="wa-loading-separator" aria-hidden="true">·</span>
              <span className="wa-loading-timer" aria-hidden="true">
                {loadingElapsed}s
              </span>
            </div>
            <p className="wa-loading-note" aria-live="off">
              {loadingElapsed >= 45
                ? "Taking longer than usual — you can wait or try another server."
                : "This can take up to a minute."}
            </p>
            <button
              type="button"
              className="wa-text-btn"
              onClick={onBack}
              data-focusable="true"
            >
              Back to details
            </button>
          </div>
        </div>
      ) : null}

      {!loadingVisible && fatalError ? (
        <div
          className="wa-state wa-error"
          role="alert"
          aria-labelledby="wa-error-title"
          aria-describedby="wa-error-message"
        >
          <div ref={errorCardRef} className="wa-error-card">
            <span className="wa-error-icon" aria-hidden="true">
              <X size={30} />
            </span>
            <h2 id="wa-error-title">{fatalError.title}</h2>
            <p id="wa-error-message">{fatalError.message}</p>
            <div className="wa-error-actions">
              {preferOtherSource ? (
                <>
                  <button
                    ref={errorPrimaryRef}
                    type="button"
                    className="wa-primary-btn"
                    onClick={handleTryOtherSource}
                    disabled={serverIsBusy}
                    data-focusable="true"
                  >
                    {tryOtherLabel}
                  </button>
                  <button
                    type="button"
                    className="wa-secondary-btn"
                    onClick={handleRetryAction}
                    disabled={serverIsBusy}
                    data-focusable="true"
                  >
                    Try again
                  </button>
                </>
              ) : (
                <>
                  <button
                    ref={errorPrimaryRef}
                    type="button"
                    className="wa-primary-btn"
                    onClick={handleRetryAction}
                    disabled={serverIsBusy}
                    data-focusable="true"
                  >
                    Try again
                  </button>
                  {canTryOtherSource ? (
                    <button
                      type="button"
                      className="wa-secondary-btn"
                      onClick={handleTryOtherSource}
                      disabled={serverIsBusy}
                      data-focusable="true"
                    >
                      {tryOtherLabel}
                    </button>
                  ) : null}
                </>
              )}
              <button
                type="button"
                className="wa-secondary-btn"
                onClick={openSettings}
                disabled={serverIsBusy}
                data-focusable="true"
              >
                Choose server
              </button>
              <button
                type="button"
                className="wa-secondary-btn wa-error-back"
                onClick={onBack}
                data-focusable="true"
              >
                Go back
              </button>
            </div>
            {bothSourcesFailed && !serverIsBusy ? (
              <p className="wa-error-note">
                Both server lists were tried. Try again, choose a specific
                server, or go back.
              </p>
            ) : null}
            {serverIsBusy ? (
              <p className="wa-error-note">
                <Loader2 size={15} className="wa-spin" /> Switching to{" "}
                {serverDisplayName(switchingServer)}…
              </p>
            ) : null}
          </div>
        </div>
      ) : null}

      {!loadingVisible && !fatalError && buffering && !showCenterPlay ? (
        <div className="wa-buffering" role="status" aria-live="polite">
          <div className="wa-loader-ring wa-loader-small" aria-hidden="true" />
          <span>Buffering</span>
        </div>
      ) : null}

      {showCenterPlay ? (
        <div className="wa-center">
          <button
            ref={centerPlayRef}
            type="button"
            className="wa-center-play"
            onClick={handleTogglePlay}
            aria-label="Play"
            data-focusable="true"
          >
            <Play size={profile.isTV ? 46 : 34} fill="currentColor" />
          </button>
          {snapshot.autoplayBlocked ? (
            <p className="wa-center-hint">Tap to play</p>
          ) : null}
        </div>
      ) : null}

      {!loadingVisible && !fatalError && isEnded ? (
        <div className="wa-state wa-ended">
          <div className="wa-ended-card">
            <p className="wa-ended-kicker">Finished</p>
            <h2>{title}</h2>
            <div className="wa-error-actions">
              <button
                type="button"
                className="wa-primary-btn"
                onClick={() => {
                  seek(0);
                  play();
                  showUi();
                }}
                data-focusable="true"
              >
                <RotateCcw size={18} /> Watch again
              </button>
              <button
                type="button"
                className="wa-secondary-btn"
                onClick={onBack}
                data-focusable="true"
              >
                Back to details
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {!loadingVisible && !fatalError && !isEnded ? (
        <div className="wa-controls">
          <div
            className="wa-bottom"
            onPointerEnter={() => setHoverChrome(true)}
            onPointerLeave={() => setHoverChrome(false)}
          >
            <div className="wa-controls-inner">
            <PlayerTimeline
              currentTime={timelineCurrent}
              duration={timelineDuration}
              buffered={timelineBuffered}
              isLive={snapshot.isLive}
              onSeek={handleTimelineSeek}
              onSeekBy={flashSeek}
            />

            <div className="wa-control-row">
              <div className="wa-control-group">
                <button
                  ref={playBtnRef}
                  type="button"
                  className="wa-icon-btn wa-play-btn"
                  onClick={handleTogglePlay}
                  aria-label={isPlaying ? "Pause" : "Play"}
                  title={isPlaying ? "Pause" : "Play"}
                  data-focusable="true"
                >
                  {isPlaying ? (
                    <Pause size={24} fill="currentColor" />
                  ) : (
                    <Play size={24} fill="currentColor" />
                  )}
                </button>

                <button
                  type="button"
                  className="wa-icon-btn wa-skip-btn"
                  onClick={() => flashSeek(-10)}
                  aria-label="Back 10 seconds"
                  title="Back 10 seconds"
                  data-focusable="true"
                >
                  <RotateCcw size={20} />
                  <span className="wa-skip-label">10</span>
                </button>

                <button
                  type="button"
                  className="wa-icon-btn wa-skip-btn wa-skip-forward"
                  onClick={() => flashSeek(10)}
                  aria-label="Forward 10 seconds"
                  title="Forward 10 seconds"
                  data-focusable="true"
                >
                  <RotateCw size={20} />
                  <span className="wa-skip-label">10</span>
                </button>

                <div className="wa-volume-group">
                  <button
                    type="button"
                    className="wa-icon-btn"
                    onClick={handleToggleMute}
                    aria-label={snapshot.muted ? "Unmute" : "Mute"}
                    aria-pressed={snapshot.muted}
                    title={snapshot.muted ? "Unmute" : "Mute"}
                    data-focusable="true"
                  >
                    {volumeIcon}
                  </button>
                  {!profile.isIOS ? (
                    <input
                      type="range"
                      className="wa-volume-range"
                      min="0"
                      max="1"
                      step="0.05"
                      value={snapshot.muted ? 0 : snapshot.volume}
                      onChange={(event) => {
                        setVolume(Number(event.target.value));
                        showUi();
                      }}
                      aria-label="Volume"
                      data-focusable="true"
                    />
                  ) : null}
                </div>

                <div className="wa-time" aria-live="off">
                  {snapshot.isLive ? (
                    atLiveEdge ? (
                      <span className="wa-live-time">
                        <span className="wa-live-dot" /> LIVE
                      </span>
                    ) : (
                      <span>{formatTime(Math.max(0, snapshot.seekableEnd - snapshot.currentTime))} behind</span>
                    )
                  ) : (
                    <>
                      <span>{formatTime(timelineCurrent)}</span>
                      <span className="wa-time-divider">/</span>
                      <span>{formatTime(timelineDuration)}</span>
                    </>
                  )}
                </div>
              </div>

              <div className="wa-control-group wa-control-group-right">
                {snapshot.isLive ? (
                  atLiveEdge ? (
                    <span className="wa-live-pill">
                      <span className="wa-live-dot" /> LIVE
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="wa-pill-btn"
                      onClick={goLive}
                      data-focusable="true"
                    >
                      Go live
                    </button>
                  )
                ) : null}

                {hasCaptions ? (
                  <button
                    type="button"
                    className={`wa-icon-btn ${captionsOn ? "is-active" : ""}`}
                    onClick={handleToggleCaptions}
                    aria-label="Subtitles"
                    aria-pressed={captionsOn}
                    title="Subtitles"
                    data-focusable="true"
                  >
                    <Subtitles size={22} />
                  </button>
                ) : null}

                <button
                  ref={settingsBtnRef}
                  type="button"
                  className="wa-icon-btn"
                  onClick={openSettings}
                  aria-label="Settings"
                  title="Settings"
                  data-focusable="true"
                >
                  <Settings size={22} />
                </button>

                {!profile.isTV ? (
                  <button
                    type="button"
                    className="wa-icon-btn"
                    onClick={toggleFullscreen}
                    aria-label={isFullscreen ? "Exit fullscreen" : "Fullscreen"}
                    title={isFullscreen ? "Exit fullscreen" : "Fullscreen"}
                    data-focusable="true"
                  >
                    {isFullscreen ? <Minimize size={22} /> : <Maximize size={22} />}
                  </button>
                ) : null}
              </div>
            </div>
            </div>
          </div>
        </div>
      ) : null}

      {seekFlash ? (
        <div
          key={seekFlash.id}
          className={`wa-seek-flash wa-seek-${seekFlash.seconds > 0 ? "right" : "left"}`}
          aria-hidden="true"
        >
          {seekFlash.seconds > 0 ? <RotateCw size={28} /> : <RotateCcw size={28} />}
          <span>{Math.abs(seekFlash.seconds)} seconds</span>
        </div>
      ) : null}

      {gestureHud ? (
        <div className="wa-gesture-hud" aria-hidden="true">
          {gestureHud.kind === "volume-hint" ? (
            <>
              <Volume2 size={24} />
              <span>Use device volume</span>
            </>
          ) : (
            <>
              {gestureHud.kind === "brightness" ? (
                <Sun size={24} />
              ) : gestureHud.value === 0 ? (
                <VolumeX size={24} />
              ) : (
                <Volume2 size={24} />
              )}
              <div className="wa-gesture-bar">
                <div
                  className="wa-gesture-fill"
                  style={{ width: `${clamp(gestureHud.value, 0, 1) * 100}%` }}
                />
              </div>
              <span>{gestureHud.kind === "brightness" ? "Brightness" : `${Math.round(gestureHud.value * 100)}%`}</span>
            </>
          )}
        </div>
      ) : null}

      {toast ? (
        <div
          className={`wa-toast wa-toast-${toast.tone || "default"}`}
          role={toast.tone === "error" ? "alert" : "status"}
        >
          <span>{toast.message}</span>
          {toast.actionLabel ? (
            <button
              type="button"
              className="wa-toast-action"
              onClick={() => {
                const action = toast.onAction;
                setToast(null);
                clearTimeout(toastTimerRef.current);
                action?.();
              }}
            >
              {toast.actionLabel}
            </button>
          ) : null}
          <button
            type="button"
            className="wa-toast-close"
            onClick={() => setToast(null)}
            aria-label="Dismiss message"
          >
            <X size={16} />
          </button>
        </div>
      ) : null}

      <div key={announcement.id} className="wa-sr-only" aria-live="polite">
        {announcement.text}
      </div>

      {settingsOpen ? (
        <PlayerSettings
          snapshot={snapshot}
          servers={servers}
          serversReady={serversReady}
          activeServer={activeServer}
          switchingServer={switchingServer}
          onSelectServer={onSelectServer}
          onClose={closeSettings}
          onOpenHelp={openHelp}
          onAction={handleSettingsAction}
          profile={profile}
          captionSize={captionSize}
          settingsRef={settingsRef}
        />
      ) : null}

      {helpOpen ? (
        <div className="wa-help-layer">
          <button
            type="button"
            className="wa-sheet-backdrop"
            tabIndex={-1}
            aria-label="Close keyboard shortcuts"
            onClick={closeHelp}
          />
          <div
            ref={helpRef}
            className="wa-help"
            role="dialog"
            aria-modal="true"
            aria-labelledby="wa-help-title"
          >
            <div className="wa-sheet-header">
              <h2 id="wa-help-title">Keyboard shortcuts</h2>
              <button
                type="button"
                className="wa-icon-btn"
                onClick={closeHelp}
                aria-label="Close keyboard shortcuts"
                data-focusable="true"
              >
                <X size={22} />
              </button>
            </div>
            <dl className="wa-help-list">
              {shortcuts.map(([keys, description]) => (
                <div key={keys} className="wa-help-row">
                  <dt>
                    <kbd>{keys}</kbd>
                  </dt>
                  <dd>{description}</dd>
                </div>
              ))}
            </dl>
            <p className="wa-help-note">
              On a TV, use the arrow keys to move focus, OK or Enter to select,
              and Back or Escape to close menus.
            </p>
          </div>
        </div>
      ) : null}

    </div>
  );
}
