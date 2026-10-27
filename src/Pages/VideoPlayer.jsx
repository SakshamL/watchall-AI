import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import WatchPlayer from "../Components/Player/WatchPlayer";
import { readStore, resumeKey } from "../Components/Player/playerUtils";
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
  const switchAbortRef = useRef(null);
  const languageRef = useRef("");
  const autoFallbackRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const stored = readStore(resumeKey(id), null);
    const startAt = Number(stored?.position);

    autoFallbackRef.current = false;
    setInitialTime(Number.isFinite(startAt) && startAt > 5 ? startAt : 0);
    setSource(null);
    setSourceError(null);
    setSourceLoading(true);
    setActiveServer(null);
    setSwitching(null);
    setNotice(null);
    setLoadingMessage("Finding a working stream…");

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

      try {
        const response = await fetch(
          `/api/extract?tmdb=${encodeURIComponent(id)}&type=movie${langParam}`,
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
              "No playable stream was found. Try again or choose another server."
            )
          );
        }

        setSource({
          streamUrl: data.streamUrl,
          type: data.type,
          server: data.server || null,
        });
        setActiveServer(data.server || null);
        setSourceError(null);
        setSourceLoading(false);
        setLoadingMessage("");
      } catch (error) {
        if (cancelled || error.name === "AbortError") return;
        setSourceError({
          title: "Unable to start playback",
          message:
            error.message ||
            "The stream could not be started. Try again or choose another server.",
        });
        setSourceLoading(false);
      }
    };

    start();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [id, retryToken]);

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

  const switchServer = useCallback(
    async (name) => {
      if (!name) return;
      if (name === activeServer) {
        setNotice({
          id: Date.now(),
          text: `Already playing from ${name}.`,
          tone: "default",
        });
        return;
      }
      if (switching) return;

      switchAbortRef.current?.abort();
      const controller = new AbortController();
      switchAbortRef.current = controller;
      setSwitching(name);

      const lang = languageRef.current;
      const langParam = lang ? `&lang=${encodeURIComponent(lang)}` : "";

      try {
        const response = await fetch(
          `/api/extract?tmdb=${encodeURIComponent(id)}&type=movie&srv=${encodeURIComponent(name)}${langParam}`,
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

        setSource({
          streamUrl: data.streamUrl,
          type: data.type,
          server: data.server || name,
        });
        setActiveServer(data.server || name);
        setSourceError(null);
        setSourceLoading(false);
        setNotice({
          id: Date.now(),
          text: `Now playing from ${data.server || name}.`,
          tone: "success",
        });
      } catch (error) {
        if (controller.signal.aborted || error.name === "AbortError") return;
        setNotice({
          id: Date.now(),
          text:
            error.message ||
            `Could not switch to ${name}. Please try another server.`,
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
    [activeServer, id, switching]
  );

  const handleFatalPlaybackError = useCallback(
    (error, failedSource) => {
      if (autoFallbackRef.current || servers.length === 0) return;
      const current = failedSource?.server || activeServer;
      const currentIndex = servers.findIndex((item) => item.name === current);
      const next =
        servers[currentIndex >= 0 ? (currentIndex + 1) % servers.length : 0];
      if (!next || next.name === current) return;

      autoFallbackRef.current = true;
      setNotice({
        id: Date.now(),
        text: `${error?.title || "Playback failed"}. Trying ${next.name}…`,
        tone: "error",
        duration: 5000,
      });
      switchServer(next.name);
    },
    [activeServer, servers, switchServer]
  );

  const handleRetrySource = useCallback(() => {
    setRetryToken((value) => value + 1);
  }, []);

  const handleBack = useCallback(() => {
    if (window.history.length > 1) navigate(-1);
    else navigate("/");
  }, [navigate]);

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
        mediaId={`movie-${id}`}
        initialTime={initialTime}
        servers={servers}
        serversReady={serversReady}
        activeServer={activeServer}
        switchingServer={switching}
        onSelectServer={switchServer}
        onRetrySource={handleRetrySource}
        onBack={handleBack}
        onFatalPlaybackError={handleFatalPlaybackError}
        notice={notice}
      />
    </>
  );
};

export default VideoPlayer;
