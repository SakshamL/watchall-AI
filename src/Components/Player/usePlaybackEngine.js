import { useCallback, useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import {
  captionsKey,
  clamp,
  detectSourceType,
  hlsConfigFor,
  maxQualityIndex,
  prefsKey,
  readStore,
  sniffSourceType,
  streamUrlFor,
  writeStore,
} from "./playerUtils";

const makeSnapshot = () => ({
  status: "idle",
  engine: "",
  currentTime: 0,
  duration: 0,
  bufferedEnd: 0,
  seekableStart: 0,
  seekableEnd: 0,
  volume: 1,
  muted: false,
  playbackRate: 1,
  isLive: false,
  qualities: [],
  quality: -1,
  audioTracks: [],
  audioTrack: -1,
  textTracks: [],
  textTrack: -1,
  error: null,
  autoplayBlocked: false,
  isBuffering: false,
  isSeeking: false,
  bitrate: 0,
});

const mediaErrorFor = (video) => {
  const error = video?.error;
  if (!error) return null;
  if (error.code === 1) return null;
  if (error.code === 2) {
    return {
      code: "NETWORK_ERROR",
      title: "Playback interrupted",
      message: "The stream stopped responding. Try again or choose another server.",
    };
  }
  if (error.code === 3) {
    return {
      code: "DECODE_ERROR",
      title: "This device cannot decode the stream",
      message: "Try another server or a lower quality stream.",
    };
  }
  if (error.code === 4) {
    return {
      code: "UNSUPPORTED",
      title: "This video format is not supported",
      message: "Try another server. Some TV browsers cannot play every codec.",
    };
  }
  return {
    code: "MEDIA_ERROR",
    title: "Something went wrong",
    message: "Playback could not continue. Try again or switch servers.",
  };
};

export function usePlaybackEngine({ source, initialTime = 0, profile, onFatalError, mediaId }) {
  const videoRef = useRef(null);
  const hlsRef = useRef(null);
  const dashRef = useRef(null);
  const profileRef = useRef(profile);
  const onFatalRef = useRef(onFatalError);
  const sourceRef = useRef(source);
  const mediaIdRef = useRef(mediaId);
  const pendingStartRef = useRef(Number(initialTime) || 0);
  const initialTimeRef = useRef(Number(initialTime) || 0);
  const timeRef = useRef(0);
  const attachedRef = useRef(false);
  const fatalRef = useRef(false);
  const resumeEligibleRef = useRef(true);
  const captionsPrefRef = useRef(!!readStore(captionsKey, false));
  const prefsRef = useRef(null);
  const snapshotRef = useRef(null);
  const [snapshot, setSnapshot] = useState(makeSnapshot);
  const [resumedFrom, setResumedFrom] = useState(0);
  const [reloadToken, setReloadToken] = useState(0);

  profileRef.current = profile;
  onFatalRef.current = onFatalError;
  sourceRef.current = source;
  mediaIdRef.current = mediaId;
  snapshotRef.current = snapshot;

  if (!prefsRef.current) {
    const stored = readStore(prefsKey, {});
    prefsRef.current = {
      volume: clamp(Number(stored?.volume ?? 1), 0, 1),
      muted: !!stored?.muted,
      rate: clamp(Number(stored?.rate ?? 1), 0.25, 2),
    };
  }

  const patch = useCallback((updates) => {
    setSnapshot((previous) => ({ ...previous, ...updates }));
  }, []);

  const requestPlay = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    patch({ autoplayBlocked: false });
    try {
      const playPromise = video.play();
      if (playPromise && typeof playPromise.catch === "function") {
        playPromise.catch(() => {
          setSnapshot((previous) =>
            previous.status === "playing"
              ? previous
              : { ...previous, autoplayBlocked: true, status: "paused" }
          );
        });
      }
    } catch {
      setSnapshot((previous) => ({
        ...previous,
        autoplayBlocked: true,
        status: "paused",
      }));
    }
  }, [patch]);

  const failPlayback = useCallback(
    (error) => {
      if (fatalRef.current) return;
      fatalRef.current = true;
      setSnapshot((previous) => ({
        ...previous,
        status: "error",
        error,
        isBuffering: false,
      }));
      onFatalRef.current?.(error, sourceRef.current);
    },
    []
  );

  const pause = useCallback(() => {
    videoRef.current?.pause();
  }, []);

  const togglePlay = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused || video.ended) requestPlay();
    else video.pause();
  }, [requestPlay]);

  const seek = useCallback((time) => {
    const video = videoRef.current;
    if (!video) return;
    const liveEnd = video.seekable.length
      ? video.seekable.end(video.seekable.length - 1)
      : 0;
    const max = Number.isFinite(video.duration) && video.duration > 0
      ? video.duration
      : liveEnd;
    const target = clamp(Number(time) || 0, 0, Math.max(0, max - 0.1));
    try {
      video.currentTime = target;
    } catch {
      return;
    }
    setSnapshot((previous) => ({
      ...previous,
      currentTime: target,
      status: previous.status === "ended" ? "paused" : previous.status,
    }));
  }, []);

  const seekBy = useCallback(
    (delta) => {
      const video = videoRef.current;
      if (!video) return;
      seek(video.currentTime + delta);
    },
    [seek]
  );

  const setVolume = useCallback((value) => {
    const video = videoRef.current;
    if (!video) return;
    const next = clamp(Number(value), 0, 1);
    try {
      video.volume = next;
      if (next > 0 && video.muted) video.muted = false;
    } catch {
      return;
    }
    setSnapshot((previous) => ({
      ...previous,
      volume: next,
      muted: next === 0,
    }));
  }, []);

  const toggleMute = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    try {
      video.muted = !video.muted;
      if (!video.muted && video.volume === 0) video.volume = 0.5;
    } catch {
      return;
    }
    setSnapshot((previous) => ({
      ...previous,
      muted: video.muted,
      volume: video.volume,
    }));
  }, []);

  const setPlaybackRate = useCallback((rate) => {
    const video = videoRef.current;
    if (!video) return;
    const next = clamp(Number(rate), 0.25, 2);
    try {
      video.playbackRate = next;
    } catch {
      return;
    }
    setSnapshot((previous) => ({ ...previous, playbackRate: next }));
    writeStore(prefsKey, { ...prefsRef.current, rate: next });
    prefsRef.current.rate = next;
  }, []);

  const setQuality = useCallback((index) => {
    const quality = Number(index);
    if (hlsRef.current) {
      hlsRef.current.currentLevel = quality;
      setSnapshot((previous) => ({ ...previous, quality }));
      return;
    }
    const player = dashRef.current;
    if (player) {
      try {
        player.updateSettings({
          streaming: { abr: { autoSwitchBitrate: { video: quality === -1 } } },
        });
        if (quality >= 0) {
          player.setRepresentationForTypeByIndex("video", quality, true);
        }
      } catch (error) {
        void error;
      }
      setSnapshot((previous) => ({ ...previous, quality }));
    }
  }, []);

  const setAudioTrack = useCallback((index) => {
    const trackIndex = Number(index);
    const hls = hlsRef.current;
    if (hls) {
      try {
        hls.audioTrack = trackIndex;
      } catch (error) {
        void error;
      }
      setSnapshot((previous) => ({ ...previous, audioTrack: trackIndex }));
      return;
    }
    const player = dashRef.current;
    if (player) {
      try {
        const tracks = player.getTracksFor("audio");
        if (tracks[trackIndex]) player.setCurrentTrack(tracks[trackIndex]);
      } catch (error) {
        void error;
      }
      setSnapshot((previous) => ({ ...previous, audioTrack: trackIndex }));
      return;
    }
    const video = videoRef.current;
    if (video?.audioTracks) {
      Array.from(video.audioTracks).forEach((track, trackIdx) => {
        track.enabled = trackIdx === trackIndex;
      });
      setSnapshot((previous) => ({ ...previous, audioTrack: trackIndex }));
    }
  }, []);

  const setTextTrack = useCallback((index) => {
    const trackIndex = Number(index);
    captionsPrefRef.current = trackIndex >= 0;
    writeStore(captionsKey, captionsPrefRef.current);

    const hls = hlsRef.current;
    if (hls) {
      try {
        hls.subtitleTrack = trackIndex;
        hls.subtitleDisplay = trackIndex >= 0;
      } catch (error) {
        void error;
      }
      setSnapshot((previous) => ({ ...previous, textTrack: trackIndex }));
      return;
    }

    const player = dashRef.current;
    if (player) {
      try {
        player.setTextTrack(trackIndex);
      } catch (error) {
        void error;
      }
      setSnapshot((previous) => ({ ...previous, textTrack: trackIndex }));
      return;
    }

    const video = videoRef.current;
    if (video?.textTracks) {
      Array.from(video.textTracks).forEach((track, trackIdx) => {
        track.mode = trackIdx === trackIndex ? "showing" : "disabled";
      });
      setSnapshot((previous) => ({ ...previous, textTrack: trackIndex }));
    }
  }, []);

  const toggleCaptions = useCallback(() => {
    const current = snapshotRef.current;
    if (!current || current.textTracks.length === 0) return;
    if (current.textTrack >= 0) setTextTrack(-1);
    else setTextTrack(0);
  }, [setTextTrack]);

  const goLive = useCallback(() => {
    const video = videoRef.current;
    if (!video || !video.seekable.length) return;
    const edge = video.seekable.end(video.seekable.length - 1);
    try {
      video.currentTime = Math.max(0, edge - 1);
    } catch {
      return;
    }
    requestPlay();
  }, [requestPlay]);

  const retry = useCallback(() => {
    fatalRef.current = false;
    setSnapshot((previous) => ({
      ...previous,
      status: "loading",
      error: null,
      autoplayBlocked: false,
      isBuffering: false,
    }));
    setReloadToken((value) => value + 1);
  }, []);

  const clearResumeNotice = useCallback(() => setResumedFrom(0), []);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return undefined;
    const prefs = prefsRef.current;
    try {
      video.volume = prefs.volume;
      video.muted = prefs.muted;
      video.playbackRate = prefs.rate;
    } catch (error) {
      void error;
    }
    patch({ volume: prefs.volume, muted: prefs.muted, playbackRate: prefs.rate });
    return undefined;
  }, [patch]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return undefined;

    let lastBufferedUpdate = 0;

    const applyDuration = () => {
      const finite = Number.isFinite(video.duration) && video.duration > 0;
      setSnapshot((previous) => ({
        ...previous,
        duration: finite ? video.duration : previous.duration,
        isLive: previous.isLive || video.duration === Infinity,
      }));
    };

    const updateBuffered = (force) => {
      const now = Date.now();
      if (!force && now - lastBufferedUpdate < 350) return;
      lastBufferedUpdate = now;
      let bufferedEnd = 0;
      for (let index = 0; index < video.buffered.length; index += 1) {
        bufferedEnd = Math.max(bufferedEnd, video.buffered.end(index));
      }
      const seekableStart = video.seekable.length ? video.seekable.start(0) : 0;
      const seekableEnd = video.seekable.length
        ? video.seekable.end(video.seekable.length - 1)
        : 0;
      setSnapshot((previous) => ({
        ...previous,
        bufferedEnd,
        seekableStart,
        seekableEnd,
      }));
    };

    const onLoadedMetadata = () => {
      applyDuration();
      const startAt = pendingStartRef.current;
      if (startAt > 0.5) {
        const limit =
          Number.isFinite(video.duration) && video.duration > 0
            ? Math.max(0, video.duration - 5)
            : startAt;
        const target = Math.min(startAt, limit);
        if (target > 0.5) {
          try {
            video.currentTime = target;
          } catch (error) {
            void error;
          }
          if (resumeEligibleRef.current && target > 30) {
            setResumedFrom(target);
          }
        }
      }
      pendingStartRef.current = 0;
      initialTimeRef.current = 0;
      resumeEligibleRef.current = false;
      updateBuffered(true);
    };

    const onDurationChange = () => applyDuration();

    const onTimeUpdate = () => {
      timeRef.current = video.currentTime;
      updateBuffered(false);
      setSnapshot((previous) => ({
        ...previous,
        currentTime: video.currentTime,
        duration:
          Number.isFinite(video.duration) && video.duration > 0
            ? video.duration
            : previous.duration,
      }));
    };

    const onProgress = () => updateBuffered(false);

    const onPlay = () => {
      patch({ status: "playing", autoplayBlocked: false, isBuffering: false });
    };

    const onPlaying = () => {
      patch({ status: "playing", autoplayBlocked: false, isBuffering: false });
    };

    const onPause = () => {
      if (video.ended) return;
      patch({ status: "paused", isBuffering: false });
    };

    const onWaiting = () => {
      if (!video.paused) patch({ isBuffering: true });
    };

    const onCanPlay = () => patch({ isBuffering: false });

    const onSeeking = () => patch({ isSeeking: true });
    const onSeeked = () => patch({ isSeeking: false });

    const onEnded = () => patch({ status: "ended", isBuffering: false });

    const onVolumeChange = () => {
      const prefs = {
        ...prefsRef.current,
        volume: video.volume,
        muted: video.muted,
      };
      prefsRef.current = prefs;
      writeStore(prefsKey, prefs);
      patch({ volume: video.volume, muted: video.muted });
    };

    const onRateChange = () => {
      patch({ playbackRate: video.playbackRate });
    };

    const onVideoError = () => {
      const error = mediaErrorFor(video);
      if (error) failPlayback(error);
    };

    video.addEventListener("loadedmetadata", onLoadedMetadata);
    video.addEventListener("durationchange", onDurationChange);
    video.addEventListener("timeupdate", onTimeUpdate);
    video.addEventListener("progress", onProgress);
    video.addEventListener("play", onPlay);
    video.addEventListener("playing", onPlaying);
    video.addEventListener("pause", onPause);
    video.addEventListener("waiting", onWaiting);
    video.addEventListener("stalled", onWaiting);
    video.addEventListener("canplay", onCanPlay);
    video.addEventListener("seeking", onSeeking);
    video.addEventListener("seeked", onSeeked);
    video.addEventListener("ended", onEnded);
    video.addEventListener("volumechange", onVolumeChange);
    video.addEventListener("ratechange", onRateChange);
    video.addEventListener("error", onVideoError);

    return () => {
      video.removeEventListener("loadedmetadata", onLoadedMetadata);
      video.removeEventListener("durationchange", onDurationChange);
      video.removeEventListener("timeupdate", onTimeUpdate);
      video.removeEventListener("progress", onProgress);
      video.removeEventListener("play", onPlay);
      video.removeEventListener("playing", onPlaying);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("waiting", onWaiting);
      video.removeEventListener("stalled", onWaiting);
      video.removeEventListener("canplay", onCanPlay);
      video.removeEventListener("seeking", onSeeking);
      video.removeEventListener("seeked", onSeeked);
      video.removeEventListener("ended", onEnded);
      video.removeEventListener("volumechange", onVolumeChange);
      video.removeEventListener("ratechange", onRateChange);
      video.removeEventListener("error", onVideoError);
    };
  }, [failPlayback, patch]);

  useEffect(() => {
    setResumedFrom(0);
    resumeEligibleRef.current = true;
    initialTimeRef.current = Number(initialTime) || 0;
    pendingStartRef.current = Number(initialTime) || 0;
  }, [mediaId, initialTime]);

  const sourceUrl = source?.streamUrl || "";
  const sourceType = source?.type || "";
  const sourceKey = `${sourceUrl}|${sourceType}`;

  useEffect(() => {
    const video = videoRef.current;

    const destroyEngines = () => {
      if (hlsRef.current) {
        try {
          hlsRef.current.destroy();
        } catch (error) {
          void error;
        }
        hlsRef.current = null;
      }
      if (dashRef.current) {
        try {
          dashRef.current.reset();
        } catch (error) {
          void error;
        }
        dashRef.current = null;
      }
    };

    if (!video) return undefined;

    if (attachedRef.current) {
      const captured = video.currentTime;
      if (Number.isFinite(captured) && captured > 0.5) {
        pendingStartRef.current = captured;
        initialTimeRef.current = 0;
      }
      attachedRef.current = false;
    }

    destroyEngines();
    try {
      video.pause();
    } catch (error) {
      void error;
    }
    video.removeAttribute("src");
    try {
      video.load();
    } catch (error) {
      void error;
    }
    fatalRef.current = false;

    setSnapshot((previous) => ({
      ...makeSnapshot(),
      volume: previous.volume,
      muted: previous.muted,
      playbackRate: previous.playbackRate,
      status: sourceUrl ? "loading" : "idle",
    }));

    if (!sourceUrl) {
      return () => {
        destroyEngines();
      };
    }

    let cancelled = false;
    let networkRetries = 0;
    let mediaRetries = 0;
    const device = profileRef.current;

    const tryAutoplay = () => {
      if (cancelled) return;
      const playPromise = video.play();
      if (playPromise && typeof playPromise.catch === "function") {
        playPromise.catch(() => {
          if (cancelled) return;
          setSnapshot((previous) =>
            previous.status === "playing"
              ? previous
              : { ...previous, autoplayBlocked: true, status: "paused" }
          );
        });
      }
    };

    const attachNative = (mediaUrl, nativeType) => {
      attachedRef.current = true;
      video.src = mediaUrl;
      video.load();
      patch({ engine: nativeType || "native" });

      const syncNativeTracks = () => {
        const textTracks = video.textTracks
          ? Array.from(video.textTracks).map((track, index) => ({
              id: index,
              label: track.label,
              language: track.language,
              kind: track.kind,
              mode: track.mode,
            }))
          : [];
        const audioTracks = video.audioTracks
          ? Array.from(video.audioTracks).map((track, index) => ({
              id: index,
              label: track.label,
              language: track.language,
              kind: track.kind,
            }))
          : [];
        const activeText = textTracks.findIndex((track) => track.mode === "showing");
        let textTrack = activeText;
        if (textTrack < 0 && captionsPrefRef.current && textTracks.length) {
          textTrack = 0;
          video.textTracks[0].mode = "showing";
        }
        const activeAudio = audioTracks.findIndex(
          (_, index) => video.audioTracks?.[index]?.enabled
        );
        patch({
          textTracks,
          textTrack,
          audioTracks,
          audioTrack: activeAudio,
        });
      };

      const onNativeReady = () => {
        syncNativeTracks();
        tryAutoplay();
      };

      video.addEventListener("loadedmetadata", onNativeReady, { once: true });
      const trackList = video.textTracks;
      if (trackList?.addEventListener) {
        trackList.addEventListener("change", syncNativeTracks);
        trackList.addEventListener("addtrack", syncNativeTracks);
      }
      const cleanupTracks = () => {
        video.removeEventListener("loadedmetadata", onNativeReady);
        if (trackList?.removeEventListener) {
          trackList.removeEventListener("change", syncNativeTracks);
          trackList.removeEventListener("addtrack", syncNativeTracks);
        }
      };
      return cleanupTracks;
    };

    const attachHls = (mediaUrl) => {
      if (Hls.isSupported()) {
        attachedRef.current = true;
        const hls = new Hls(hlsConfigFor(device));
        hlsRef.current = hls;

        const syncTracks = () => {
          const audioTracks = (hls.audioTracks || []).map((track, index) => ({
            id: index,
            label: track.name || track.lang,
            language: track.lang,
            kind: "audio",
          }));
          const textTracks = (hls.subtitleTracks || []).map((track, index) => ({
            id: index,
            label:
              track.name ||
              track.attrs?.NAME ||
              track.lang ||
              track.attrs?.LANGUAGE,
            language: track.lang || track.attrs?.LANGUAGE,
            kind: track.attrs?.CHARACTERISTICS || "subtitles",
          }));
          let textTrack = hls.subtitleTrack;
          if (textTrack < 0 && captionsPrefRef.current && textTracks.length) {
            textTrack = 0;
            try {
              hls.subtitleDisplay = true;
              hls.subtitleTrack = 0;
            } catch (error) {
              void error;
            }
          }
          patch({
            audioTracks,
            audioTrack: hls.audioTrack ?? -1,
            textTracks,
            textTrack,
          });
        };

        hls.on(Hls.Events.MEDIA_ATTACHED, () => {
          hls.loadSource(mediaUrl);
        });

        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          const qualities = hls.levels.map((level, index) => ({
            id: index,
            height: level.height,
            width: level.width,
            bitrate: level.bitrate,
            frameRate: level.frameRate,
          }));
          if (device.lowPower || device.isTV) {
            const cap = maxQualityIndex(qualities, 1080);
            if (cap >= 0 && cap < qualities.length - 1) hls.autoLevelCapping = cap;
          }
          patch({
            engine: "hls",
            qualities,
            quality: hls.autoLevelEnabled ? -1 : hls.currentLevel,
            duration:
              Number.isFinite(video.duration) && video.duration > 0
                ? video.duration
                : hls.levels[0]?.details?.totalduration || 0,
          });
          syncTracks();
          tryAutoplay();
        });

        hls.on(Hls.Events.LEVEL_LOADED, (_, data) => {
          if (data?.details) {
            patch({ isLive: !!data.details.live });
          }
        });

        hls.on(Hls.Events.LEVEL_SWITCHED, (_, data) => {
          const level = hls.levels[data.level];
          patch({
            quality: hls.autoLevelEnabled ? -1 : data.level,
            bitrate: level?.bitrate || 0,
          });
        });

        hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, syncTracks);
        hls.on(Hls.Events.AUDIO_TRACK_SWITCHED, (_, data) => {
          patch({ audioTrack: data.id });
        });
        hls.on(Hls.Events.SUBTITLE_TRACKS_UPDATED, syncTracks);
        hls.on(Hls.Events.SUBTITLE_TRACK_SWITCH, (_, data) => {
          if (typeof data.id === "number") patch({ textTrack: data.id });
        });

        hls.on(Hls.Events.ERROR, (_, data) => {
          if (!data?.fatal) return;
          if (data.type === Hls.ErrorTypes.NETWORK_ERROR && networkRetries < 1) {
            networkRetries += 1;
            try {
              hls.startLoad();
            } catch (error) {
              void error;
            }
            return;
          }
          if (data.type === Hls.ErrorTypes.MEDIA_ERROR && mediaRetries < 1) {
            mediaRetries += 1;
            try {
              hls.recoverMediaError();
            } catch (error) {
              void error;
            }
            return;
          }
          const network = data.type === Hls.ErrorTypes.NETWORK_ERROR;
          failPlayback({
            code: network ? "NETWORK_ERROR" : "STREAM_ERROR",
            title: network ? "Stream connection lost" : "This stream cannot play",
            message:
              data.details || "Try again or choose another server to continue.",
          });
        });

        hls.attachMedia(video);
        return () => {
          try {
            hls.destroy();
          } catch (error) {
            void error;
          }
          if (hlsRef.current === hls) hlsRef.current = null;
        };
      }

      if (video.canPlayType("application/vnd.apple.mpegurl")) {
        return attachNative(mediaUrl, "hls");
      }

      failPlayback({
        code: "HLS_UNSUPPORTED",
        title: "HLS is not supported on this device",
        message: "Try another server or update the browser.",
      });
      return undefined;
    };

    const attachDash = async (mediaUrl) => {
      try {
        const module = await import("dashjs");
        if (cancelled) return undefined;
        const dashjs = module.default || module;
        const player = dashjs.MediaPlayer().create();
        dashRef.current = player;
        attachedRef.current = true;
        const events = dashjs.MediaPlayer.events;

        try {
          player.updateSettings({
            streaming: {
              abr: { autoSwitchBitrate: { video: true, audio: true } },
              buffer: { fastSwitchEnabled: true },
            },
          });
        } catch (error) {
          void error;
        }

        const syncTracks = () => {
          let qualities = [];
          try {
            qualities = (player.getRepresentationsByType("video") || []).map(
              (item, index) => ({
                id: index,
                height: item.height,
                width: item.width,
                bitrate: item.bandwidth || item.bitrateInKbit * 1000,
              })
            );
          } catch {
            qualities = [];
          }
          let audioTracks = [];
          let textTracks = [];
          let audioTrack = -1;
          let textTrack = -1;
          try {
            audioTracks = (player.getTracksFor("audio") || []).map((track, index) => ({
              id: index,
              label: track.labels?.[0]?.text || track.lang,
              language: track.lang,
              kind: "audio",
            }));
            textTracks = (player.getTracksFor("text") || []).map((track, index) => ({
              id: index,
              label: track.labels?.[0]?.text || track.lang,
              language: track.lang,
              kind: "subtitles",
            }));
            const currentAudio = player.getCurrentTrackFor("audio");
            if (currentAudio) {
              audioTrack = (player.getTracksFor("audio") || []).findIndex(
                (track) => track.index === currentAudio.index
              );
            }
            textTrack = player.getCurrentTextTrackIndex?.() ?? -1;
          } catch (error) {
            void error;
          }
          if (textTrack < 0 && captionsPrefRef.current && textTracks.length) {
            textTrack = 0;
            try {
              player.setTextTrack(0);
            } catch (error) {
              void error;
            }
          }
          patch({ qualities, audioTracks, audioTrack, textTracks, textTrack });
        };

        player.on(events.STREAM_INITIALIZED, () => {
          patch({
            engine: "dash",
            isLive: player.isDynamic(),
            duration:
              Number.isFinite(video.duration) && video.duration > 0
                ? video.duration
                : player.duration?.() || 0,
          });
          syncTracks();
          tryAutoplay();
        });

        player.on(events.PLAYBACK_METADATA_LOADED, syncTracks);
        player.on(events.TEXT_TRACKS_ADDED, syncTracks);
        player.on(events.TRACK_CHANGE_RENDERED, syncTracks);

        player.on(events.QUALITY_CHANGE_RENDERED, (event) => {
          if (event.mediaType && event.mediaType !== "video") return;
          const auto = !!player.getSettings()?.streaming?.abr?.autoSwitchBitrate?.video;
          patch({
            quality: auto ? -1 : Number(event.newQuality),
            bitrate:
              player.getCurrentRepresentationForType?.("video")?.bandwidth || 0,
          });
        });

        player.on(events.ERROR, (event) => {
          if (cancelled) return;
          const detail = event?.error;
          const code = Number(detail?.code);
          const message =
            detail?.message ||
            event?.event?.message ||
            "The DASH stream stopped unexpectedly.";
          const manifest = [10, 11, 12, 25, 31, 32, 34].includes(code);
          const capability = [23, 24, 35, 200, 201].includes(code);
          failPlayback({
            code: manifest
              ? "DASH_MANIFEST_ERROR"
              : capability
                ? "DASH_UNSUPPORTED"
                : "DASH_PLAYBACK_ERROR",
            title: manifest
              ? "This stream could not be loaded"
              : capability
                ? "This device cannot play this stream"
                : "Playback interrupted",
            message: `${message} Try another server if the problem continues.`,
          });
        });

        player.initialize(video, mediaUrl, false);
        return () => {
          try {
            player.reset();
          } catch (error) {
            void error;
          }
          if (dashRef.current === player) dashRef.current = null;
        };
      } catch (error) {
        console.error(error);
        failPlayback({
          code: "DASH_UNSUPPORTED",
          title: "DASH playback is not available",
          message: "Try another server or update the browser.",
        });
        return undefined;
      }
    };

    let cleanupInner = null;

    const begin = async () => {
      let type = detectSourceType(sourceUrl, sourceType);
      if (!type) type = await sniffSourceType(sourceUrl);
      if (cancelled) return;
      if (!type) {
        type = Hls.isSupported() ? "hls" : "mp4";
      }
      const mediaUrl = streamUrlFor(type, sourceUrl);
      patch({ engine: type });

      if (type === "hls") cleanupInner = attachHls(mediaUrl);
      else if (type === "dash") cleanupInner = await attachDash(mediaUrl);
      else cleanupInner = attachNative(mediaUrl, "mp4");
    };

    begin().catch((error) => {
      console.error(error);
      failPlayback({
        code: "SOURCE_ERROR",
        title: "Unable to start playback",
        message: "Check your connection or choose another server.",
      });
    });

    return () => {
      cancelled = true;
      if (attachedRef.current) {
        const captured = video.currentTime;
        if (Number.isFinite(captured) && captured > 0.5) {
          pendingStartRef.current = captured;
          initialTimeRef.current = 0;
        }
        attachedRef.current = false;
      }
      try {
        video.pause();
      } catch (error) {
        void error;
      }
      if (typeof cleanupInner === "function") cleanupInner();
      destroyEngines();
    };
  }, [sourceKey, reloadToken, failPlayback, patch, sourceType, sourceUrl]);

  return {
    videoRef,
    snapshot,
    resumedFrom,
    clearResumeNotice,
    play: requestPlay,
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
  };
}
