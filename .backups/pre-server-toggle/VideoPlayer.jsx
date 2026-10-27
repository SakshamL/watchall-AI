import React, { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import Hls from "hls.js";



const VideoPlayer = () => {
  const { id } = useParams();
  const videoRef = useRef(null);

  const [status, setStatus] = useState("idle"); // idle | loading | playing | error
  const [errorMsg, setErrorMsg] = useState("");

  // Clean up HLS instance on unmount
  const hlsRef = useRef(null);
  useEffect(() => {
    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
      }
    };
  }, []);

  const startPlayback = async () => {
    setStatus("loading");
    setErrorMsg("");

    try {
      // Step 1 — Ask the backend to extract the .m3u8 URL from Viduki
      const res = await fetch(`/api/extract?tmdb=${id}&type=movie`);
      const data = await res.json();

      if (!res.ok || !data.streamUrl) {
        throw new Error(data.error || "No stream URL returned");
      }

      const video = videoRef.current;
      if (!video) return;

      // MP4 fallback: no HLS playlist exists for this title — Viduki returned a
      // signed progressive MP4. The browser plays it natively (no hls.js needed)
      // and <video> without crossorigin needs no CORS headers.
      if (data.type === "mp4") {
        video.src = data.streamUrl;
        video.play().catch(() => {}); // autoplay might be blocked; user can press play
        setStatus("playing");
        return;
      }

      if (data.type === "dash") {
        throw new Error("This stream uses DASH playback, which is not supported here.");
      }

      // Step 2 — Build a proxied m3u8 URL so CORS is bypassed
      const proxiedUrl = `/api/proxy?url=${encodeURIComponent(data.streamUrl)}`;

      // Step 3 — Feed to HLS.js and attach to <video>

      if (Hls.isSupported()) {
        if (hlsRef.current) hlsRef.current.destroy();
        const hls = new Hls({
          enableWorker: true,
          startFragPrefetch: true,          // load next segment before current ends
          maxBufferLength: 60,              // buffer 60s ahead (default: 30s)
          maxMaxBufferLength: 120,          // allow up to 120s stored in memory
          maxBufferSize: 100 * 1000 * 1000, // 100MB buffer cap
          highBufferWatchdogPeriod: 4,      // check buffer health every 4s
        });
        hlsRef.current = hls;

        hls.loadSource(proxiedUrl);
        hls.attachMedia(video);

        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          video.play().catch(() => {}); // autoplay might be blocked; user can press play
          setStatus("playing");
        });

        hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal) {
            setStatus("error");
            setErrorMsg("Stream error — the video stopped unexpectedly.");
          }
        });
      } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
        // Safari native HLS
        video.src = proxiedUrl;
        video.play().catch(() => {});
        setStatus("playing");
      } else {
        throw new Error("HLS is not supported in this browser.");
      }
    } catch (err) {
      console.error(err);
      setStatus("error");
      setErrorMsg(err.message || "Something went wrong");
    }
  };

  return (
    <>
      <title>WatchAll — Watch</title>

      <div className="absolute top-0 left-0 w-[100dvw] h-[100dvh] bg-black flex items-center justify-center">

        {/* The actual <video> element — always in DOM so HLS.js can attach */}
        <video
          ref={videoRef}
          controls
          className="w-full h-full"
          style={{ display: status === "playing" ? "block" : "none" }}
        />

        {/* Overlay shown when not playing yet */}
        {status !== "playing" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-6 text-white">

            {status === "idle" && (
              <button
                onClick={startPlayback}
                className="px-10 py-4 bg-red-600 hover:bg-red-500 rounded-full text-lg font-bold transition-all hover:scale-105 shadow-lg"
              >
                ▶ Watch Now (Ad-Free)
              </button>
            )}

            {status === "loading" && (
              <div className="flex flex-col items-center gap-3">
                <div className="w-12 h-12 border-4 border-white border-t-transparent rounded-full animate-spin" />
                <p className="text-gray-300 text-sm">Fetching stream, please wait…</p>
              </div>
            )}

            {status === "error" && (
              <div className="flex flex-col items-center gap-4 max-w-sm text-center">
                <p className="text-red-400 font-semibold">{errorMsg}</p>
                <button
                  onClick={startPlayback}
                  className="px-8 py-3 bg-red-600 hover:bg-red-500 rounded-full font-bold transition-all hover:scale-105"
                >
                  Retry
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
};

export default VideoPlayer;
