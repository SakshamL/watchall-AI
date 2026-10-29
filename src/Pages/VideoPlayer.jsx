import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import WatchPlayer from "../Components/Player/WatchPlayer";
import {
  readStore,
  resumeKey,
  serverDisplayName,
} from "../Components/Player/playerUtils";
import { movieAPI } from "../Services/api";

const imageUrl = (path, size) =>
  path ? `https://image.tmdb.org/t/p/${size}${path}` : "";

const buildMetadata = (details) => {
  if (!details) return null;
  const runtime = Number(
    details.runtime || details.episode_run_time?.[0] || 0
  );
  const rating = Number(details.vote_average);
  const date = details.release_date || details.first_air_date || "";
  return {
    title: details.title || details.name || "Now playing",
    subtitle: details.tagline || details.overview || "WatchAll",
    originalLanguage:
      typeof details.original_language === "string"
        ? details.original_language
        : "",
    year: String(date).slice(0, 4),
    runtime: Number.isFinite(runtime) ? runtime : 0,
    rating: Number.isFinite(rating) && rating > 0 ? rating.toFixed(1) : "",
    poster: imageUrl(details.poster_path, "w500"),
    backdrop: imageUrl(
      details.backdrop_path || details.poster_path,
      "w1280"
    ),
  };
};

const extractionMessage = (data, fallback) =>
  (data && typeof data.error === "string" && data.error) || fallback;

const VideoPlayer = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const urlSourceValue = searchParams.get("source");
  const urlSource =
    urlSourceValue === "vidnest" || urlSourceValue === "2"
      ? "vidnest"
      : "viduki";
  const [sourceId, setSourceId] = useState(urlSource);
  const [metadata, setMetadata] = useState(null);
  const [source, setSource] = useState(null);
  const [sourceLoading, setSourceLoading] = useState(true);
  const [sourceError, setSourceError] = useState(null);
  const [loadingMessage, setLoadingMessage] = useState(
    "Finding a working stream…"
  );
  const [servers, setServers] = useState([]);
  const [serversReady, setServersReady] = useState(false);
  const [activeServer, setActiveServer] = useState(null);
  const [switching, setSwitching] = useState(null);
  const [notice, setNotice] = useState(null);
  const [initialTime, setInitialTime] = useState(0);
  const [retryToken, setRetryToken] = useState(0);
  const [failedSources, setFailedSources] = useState(() => new Set());
  const switchAbortRef = useRef(null);
  const languageRef = useRef("");
  const autoFallbackRef = useRef(false);
  const preferredServerRef = useRef("");
  const listSwitchRef = useRef("");

  const markSourceFailed = useCallback((target) => {
    if (!target) return;
    setFailedSources((previous) => {
      if (previous.has(target)) return previous;
      const next = new Set(previous);
      next.add(target);
      return next;
    });
  }, []);

  const clearSourceFailed = useCallback((target) => {
    if (!target) return;
    setFailedSources((previous) => {
      if (!previous.has(target)) return previous;
      const next = new Set(previous);
      next.delete(target);
      return next;
    });
  }, []);

  useEffect(() => {
    setSourceId(urlSource);
  }, [urlSource]);

  useEffect(() => {
    setFailedSources(new Set());
    listSwitchRef.current = "";
  }, [id]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const stored = readStore(resumeKey(id), null);
    const startAt = Number(stored?.position);

    const pendingServer =
      sourceId === "viduki" ? preferredServerRef.current : "";
    const switchingList = listSwitchRef.current === sourceId;
    preferredServerRef.current = "";
    listSwitchRef.current = "";

    autoFallbackRef.current = false;
    setInitialTime(Number.isFinite(startAt) && startAt > 5 ? startAt : 0);
    setSource(null);
    setSourceError(null);
    setSourceLoading(true);
    setActiveServer(null);
    setSwitching(null);
    setNotice(null);
    setLoadingMessage(
      switchingList
        ? sourceId === "vidnest"
          ? "Trying Server List 2…"
          : "Trying Server List 1…"
        : sourceId === "vidnest"
          ? "Getting your stream ready…"
          : pendingServer
            ? `Loading ${serverDisplayName(pendingServer)} from Server List 1…`
            : "Getting your stream ready…"
    );

    const start = async () => {
      let details = null;
      try {
        details = await movieAPI.getMovieDetails(id);
      } catch {
        details = null;
      }
      if (cancelled) return;

      if (details) {
        setMetadata(buildMetadata(details));
        languageRef.current =
          typeof details.original_language === "string"
            ? details.original_language
            : "";
      }

      const lang = languageRef.current;
      const langParam = lang ? `&lang=${encodeURIComponent(lang)}` : "";
      const serverParam = pendingServer
        ? `&srv=${encodeURIComponent(pendingServer)}`
        : "";

      try {
        const response = await fetch(
          `/api/extract?tmdb=${encodeURIComponent(id)}&type=movie&source=${sourceId}${serverParam}${langParam}`,
          { signal: controller.signal }
        );
        let data = null;
        try {
          data = await response.json();
        } catch {
          data = null;
        }
        if (cancelled) return;
        if (!response.ok || !data?.streamUrl) {
          throw new Error(
            extractionMessage(
              data,
              sourceId === "vidnest"
                ? "Server List 2 could not find a playable stream. Try Server List 1 or choose another server."
                : "Server List 1 could not find a playable stream. Try Server List 2, try again, or choose a specific server."
            )
          );
        }

        clearSourceFailed(sourceId);
        setSource({
          streamUrl: data.streamUrl,
          type: data.type,
          server: data.server || null,
          referer: data.referer || "",
          forceProxy: !!data.forceProxy || sourceId === "vidnest",
        });
        setActiveServer(
          data.server || (sourceId === "vidnest" ? "Vidnest" : null)
        );
        setSourceError(null);
        setSourceLoading(false);
        setLoadingMessage("");
      } catch (error) {
        if (cancelled || error.name === "AbortError") return;
        markSourceFailed(sourceId);
        setSourceError({
          title: "Unable to start playback",
          message:
            error.message ||
            "The stream could not be started. Try another server list or choose another server.",
        });
        setSourceLoading(false);
      }
    };

    start();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [clearSourceFailed, id, markSourceFailed, retryToken, sourceId]);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    const loadServers = async () => {
      try {
        const response = await fetch("/api/servers", {
          signal: controller.signal,
        });
        const data = await response.json();
        if (!cancelled && response.ok) {
          setServers(Array.isArray(data.servers) ? data.servers : []);
        }
      } catch {
        if (!cancelled) setServers([]);
      } finally {
        if (!cancelled) setServersReady(true);
      }
    };

    loadServers();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, []);

  useEffect(() => {
    const title = metadata?.title;
    document.title = title ? `${title} · WatchAll` : "WatchAll";
    return () => {
      document.title = "WatchAll Media";
    };
  }, [metadata]);

  useEffect(
    () => () => {
      switchAbortRef.current?.abort();
    },
    []
  );

  const selectSource = useCallback(
    (nextSource) => {
      const params = new URLSearchParams(window.location.search);
      if (nextSource === "vidnest") params.set("source", "2");
      else params.delete("source");
      const query = params.toString();
      navigate(`/movie/${id}/watch${query ? `?${query}` : ""}`, {
        replace: true,
      });
    },
    [id, navigate]
  );

  const switchServer = useCallback(
    async (name) => {
      if (!name) return;
      if (name === activeServer) {
        setNotice({
          id: Date.now(),
          text: `Already playing from ${serverDisplayName(name)}.`,
          tone: "default",
        });
        return;
      }
      if (switching) return;

      if (name === "Vidnest") {
        if (sourceId === "vidnest") return;
        preferredServerRef.current = "";
        setSourceId("vidnest");
        selectSource("vidnest");
        return;
      }

      if (sourceId === "vidnest") {
        preferredServerRef.current = name;
        setSourceId("viduki");
        selectSource("viduki");
        return;
      }

      switchAbortRef.current?.abort();
      const controller = new AbortController();
      switchAbortRef.current = controller;
      setSwitching(name);

      const lang = languageRef.current;
      const langParam = lang ? `&lang=${encodeURIComponent(lang)}` : "";

      try {
        const response = await fetch(
          `/api/extract?tmdb=${encodeURIComponent(id)}&type=movie&source=viduki&srv=${encodeURIComponent(name)}${langParam}`,
          { signal: controller.signal }
        );
        let data = null;
        try {
          data = await response.json();
        } catch {
          data = null;
        }
        if (controller.signal.aborted) return;
        if (!response.ok || !data?.streamUrl) {
          throw new Error(
            extractionMessage(data, `Could not switch to ${name}.`)
          );
        }

        clearSourceFailed(sourceId);
        setSource({
          streamUrl: data.streamUrl,
          type: data.type,
          server: data.server || name,
          referer: data.referer || "",
          forceProxy: !!data.forceProxy,
        });
        setActiveServer(data.server || name);
        setSourceError(null);
        setSourceLoading(false);
        setNotice({
          id: Date.now(),
          text: `Now playing from ${serverDisplayName(data.server || name)}.`,
          tone: "success",
        });
      } catch (error) {
        if (controller.signal.aborted || error.name === "AbortError") return;
        setNotice({
          id: Date.now(),
          text:
            error.message ||
            `Could not switch to ${serverDisplayName(name)}. Please try another server.`,
          tone: "error",
          duration: 6500,
        });
      } finally {
        if (switchAbortRef.current === controller) {
          switchAbortRef.current = null;
          setSwitching(null);
        }
      }
    },
    [activeServer, clearSourceFailed, id, selectSource, sourceId, switching]
  );

  const switchSourceList = useCallback(
    (nextSource) => {
      if (!nextSource || nextSource === sourceId) return;
      if (sourceLoading || switching) return;
      switchAbortRef.current?.abort();
      listSwitchRef.current = nextSource;
      preferredServerRef.current = "";
      setSourceError(null);
      setSourceLoading(true);
      setSwitching(null);
      setNotice(null);
      setLoadingMessage(
        nextSource === "vidnest"
          ? "Trying Server List 2…"
          : "Trying Server List 1…"
      );
      setSourceId(nextSource);
      selectSource(nextSource);
    },
    [selectSource, sourceId, sourceLoading, switching]
  );

  const sourceServers = useMemo(() => {
    const viduki = servers.map((server) => ({
      ...server,
      group: "Server List 1",
    }));
    const vidnest = [
      {
        name: "Vidnest",
        label: "Server List 2",
        language: "",
        mp4: false,
        upscale: false,
        group: "Server List 2",
      },
    ];
    return sourceId === "vidnest"
      ? [...vidnest, ...viduki]
      : [...viduki, ...vidnest];
  }, [servers, sourceId]);

  const handleFatalPlaybackError = useCallback(
    (error, failedSource) => {
      markSourceFailed(sourceId);
      if (autoFallbackRef.current || sourceId === "vidnest") return;
      const listServers = servers.filter(
        (item) => item.group === "Server List 1"
      );
      if (!listServers.length) return;
      const current = failedSource?.server || activeServer;
      const currentIndex = listServers.findIndex((item) => item.name === current);
      const next =
        listServers[
          currentIndex >= 0 ? (currentIndex + 1) % listServers.length : 0
        ];
      if (!next || next.name === current) return;

      autoFallbackRef.current = true;
      setNotice({
        id: Date.now(),
        text: `${error?.title || "Playback failed"}. Trying ${serverDisplayName(next.name)}…`,
        tone: "error",
        duration: 5000,
      });
      switchServer(next.name);
    },
    [activeServer, markSourceFailed, servers, sourceId, switchServer]
  );

  const handleRetrySource = useCallback(() => {
    listSwitchRef.current = sourceId;
    setRetryToken((value) => value + 1);
  }, [sourceId]);

  const handleBack = useCallback(() => {
    if (window.history.length > 1) navigate(-1);
    else navigate("/");
  }, [navigate]);

  const otherSourceId = sourceId === "vidnest" ? "viduki" : "vidnest";
  const otherSourceLabel =
    otherSourceId === "vidnest" ? "Server List 2" : "Server List 1";
  const currentSourceFailed =
    failedSources.has(sourceId) || Boolean(sourceError);
  const bothSourcesFailed =
    currentSourceFailed && failedSources.has(otherSourceId);

  const handleTryOtherSource = useCallback(() => {
    switchSourceList(otherSourceId);
  }, [otherSourceId, switchSourceList]);

  return (
    <>
      <title>
        {metadata?.title ? `${metadata.title} · WatchAll` : "WatchAll"}
      </title>
      <WatchPlayer
        source={source}
        sourceLoading={sourceLoading}
        sourceError={sourceError}
        loadingMessage={loadingMessage}
        metadata={metadata}
        originalLanguage={metadata?.originalLanguage || ""}
        mediaId={`movie-${id}`}
        initialTime={initialTime}
        servers={sourceServers}
        serversReady={serversReady}
        activeServer={activeServer}
        switchingServer={switching}
        onSelectServer={switchServer}
        onRetrySource={handleRetrySource}
        onTryOtherSource={handleTryOtherSource}
        otherSourceLabel={otherSourceLabel}
        bothSourcesFailed={bothSourcesFailed}
        onBack={handleBack}
        onFatalPlaybackError={handleFatalPlaybackError}
        notice={notice}
      />
    </>
  );
};

export default VideoPlayer;
