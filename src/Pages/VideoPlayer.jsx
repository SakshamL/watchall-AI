import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import WatchPlayer from "../Components/Player/WatchPlayer";
import {
  buildStreamCandidates,
  readStore,
  resumeKey,
  serverDisplayName,
} from "../Components/Player/playerUtils";
import {
  consumePrefetchedStream,
  prefetchKey,
} from "../Services/streamPrefetch";
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

const sourceForData = (data, { sourceId, server = null, forceProxy = false }) => ({
  streamUrl: data.streamUrl,
  type: data.type,
  server: data.server || server || null,
  referer: data.referer || "",
  forceProxy:
    !!data.forceProxy ||
    forceProxy ||
    sourceId === "vidnest" ||
    sourceId === "netnaija",
  candidates: buildStreamCandidates(data),
  streams: Array.isArray(data.streams) ? data.streams : [],
  captions: Array.isArray(data.captions) ? data.captions : [],
  captionsUrl: data.captionsUrl || "",
  preferDash: data.type === "dash",
});

const VideoPlayer = ({ mediaType = "movie" }) => {
  const { id } = useParams();
  const isTv = mediaType === "tv";
  const tmdbId = String(id ?? "").match(/\d+/)?.[0] || String(id ?? "").trim();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedSeason = Number(searchParams.get("season"));
  const requestedEpisode = Number(searchParams.get("episode"));
  const season =
    Number.isFinite(requestedSeason) && requestedSeason > 0 ? requestedSeason : 1;
  const episode =
    Number.isFinite(requestedEpisode) && requestedEpisode > 0
      ? requestedEpisode
      : 1;
  // Each episode keeps its own resume point and player state.
  const mediaId = isTv ? `tv-${id}-s${season}e${episode}` : `movie-${id}`;
  const urlSourceValue = searchParams.get("source");
  const urlSource =
    urlSourceValue === "vidnest" || urlSourceValue === "2"
      ? "vidnest"
      : urlSourceValue === "netnaija" ||
          urlSourceValue === "server3" ||
          urlSourceValue === "vidsrc" ||
          urlSourceValue === "3" ||
          urlSourceValue === "4"
        ? "netnaija"
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
  }, [mediaId]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const stored = readStore(resumeKey(mediaId), null);
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
          : sourceId === "netnaija"
            ? "Trying Server List 3…"
            : "Trying Server List 1…"
        : sourceId === "vidnest"
          ? "Getting your stream ready…"
          : sourceId === "netnaija"
            ? "Matching this title on Netnaija…"
            : pendingServer
    );

    const start = async () => {
      let details = null;
      try {
        details = isTv
          ? await movieAPI.getTvDetails(tmdbId)
          : await movieAPI.getMovieDetails(tmdbId);
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
      // Netnaija matches on the canonical title and release year. The server can
      // read both from TMDB, but sending what this page already has avoids a
      // second TMDB request and keeps the match exact.
      const titleParam =
        sourceId === "netnaija" && details
          ? `&title=${encodeURIComponent(
              details.title || details.name || ""
            )}&year=${encodeURIComponent(
              String(details.release_date || details.first_air_date || "").slice(
                0,
                4
              )
            )}`
          : "";
      const episodeParam = isTv ? `&season=${season}&episode=${episode}` : "";

      try {
        // TV prefetch is skipped on purpose: a card does not know which episode
        // the visitor will pick, so a series-wide prefetch would be wrong.
        const prefetched =
          pendingServer || isTv
            ? null
            : consumePrefetchedStream(
                prefetchKey({
                  mediaId: tmdbId,
                  mediaType: "movie",
                  source: sourceId,
                  language: lang,
                })
              );

        let data = prefetched;
        if (!data) {
          const response = await fetch(
            `/api/extract?tmdb=${encodeURIComponent(tmdbId)}&type=${mediaType}&source=${sourceId}${serverParam}${langParam}${titleParam}${episodeParam}`,
            { signal: controller.signal }
          );
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
                  : sourceId === "netnaija"
                    ? "Server List 3 could not find this title. Try Server List 1 or Server List 2."
                    : "Server List 1 could not find a playable stream. Try Server List 2, try again, or choose a specific server."
              )
            );
          }
        }

        clearSourceFailed(sourceId);
        setSource(sourceForData(data, { sourceId }));
        setActiveServer(
          data.server ||
            (sourceId === "vidnest"
              ? "Vidnest"
              : sourceId === "netnaija"
                ? "Netnaija"
                : null)
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
  }, [
    clearSourceFailed,
    episode,
    id,
    isTv,
    markSourceFailed,
    mediaType,
    retryToken,
    season,
    sourceId,
  ]);

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
      else if (nextSource === "netnaija") params.set("source", "3");
      else params.delete("source");
      const query = params.toString();
      navigate(`/${mediaType}/${id}/watch${query ? `?${query}` : ""}`, {
        replace: true,
      });
    },
    [id, mediaType, navigate]
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
      if (name === "Netnaija") {
        if (sourceId === "netnaija") return;
        preferredServerRef.current = "";
        setSourceId("netnaija");
        selectSource("netnaija");
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
      const episodeParam = isTv ? `&season=${season}&episode=${episode}` : "";

      try {
        const response = await fetch(
          `/api/extract?tmdb=${encodeURIComponent(tmdbId)}&type=${mediaType}&source=viduki&srv=${encodeURIComponent(name)}${langParam}${episodeParam}`,
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
        setSource(
          sourceForData(data, {
            sourceId: "viduki",
            server: name,
          })
        );
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
    [
      activeServer,
      clearSourceFailed,
      episode,
      id,
      isTv,
      mediaType,
      season,
      selectSource,
      sourceId,
      switching,
    ]
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
          : nextSource === "netnaija"
            ? "Trying Server List 3…"
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
    const netnaija = [
      {
        name: "Netnaija",
        label: "Server List 3",
        language: "",
        mp4: true,
        upscale: false,
        group: "Server List 3",
      },
    ];
    if (sourceId === "vidnest") return [...vidnest, ...viduki, ...netnaija];
    if (sourceId === "netnaija") return [...netnaija, ...viduki, ...vidnest];
    return [...viduki, ...vidnest, ...netnaija];
  }, [servers, sourceId]);

  const handleFatalPlaybackError = useCallback(
    (error, failedSource) => {
      markSourceFailed(sourceId);
      if (
        autoFallbackRef.current ||
        sourceId === "vidnest" ||
        sourceId === "netnaija"
      )
        return;
      if (activeServer) return;
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
    otherSourceId === "vidnest"
      ? "Server List 2"
      : otherSourceId === "netnaija"
        ? "Server List 3"
        : "Server List 1";
  const currentSourceFailed =
    failedSources.has(sourceId) || Boolean(sourceError);
  const bothSourcesFailed =
    currentSourceFailed && failedSources.has(otherSourceId);

  const handleTryOtherSource = useCallback(() => {
    switchSourceList(otherSourceId);
  }, [otherSourceId, switchSourceList]);

  const isEmbed = source?.type === "embed";

  return (
    <>
      <title>
        {metadata?.title ? `${metadata.title} · WatchAll` : "WatchAll"}
      </title>
      {isEmbed ? (
        <div className="wa-embed-player">
          {sourceLoading ? (
            <div className="wa-player-loading">{loadingMessage || "Loading…"}</div>
          ) : sourceError ? (
            <div className="wa-player-error">
              <h2>{sourceError.title || "Unable to load the video"}</h2>
              <p>{sourceError.message || "Try another server list."}</p>
            </div>
          ) : (
            <iframe
              src={source.streamUrl}
              title={metadata?.title || "Stream player"}
              allow="autoplay; fullscreen; picture-in-picture; encrypted-media"
              allowFullScreen
              style={{ width: "100%", height: "100%", border: 0, background: "#000" }}
            />
          )}
        </div>
      ) : (
        <WatchPlayer
          source={source}
          sourceLoading={sourceLoading}
          sourceError={sourceError}
          loadingMessage={loadingMessage}
          metadata={metadata}
          originalLanguage={metadata?.originalLanguage || ""}
          mediaId={mediaId}
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
      )}
    </>
  );
};

export default VideoPlayer;
