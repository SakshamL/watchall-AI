import React, { useState } from "react";
import ReactPlayer from "react-player";

const MovieStreamer = ({ videoId }) => {
  const [streamUrl, setStreamUrl] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchStream = async () => {
    setLoading(true);
    try {
      const response = await fetch(`/api/get-stream?id=${videoId}`);
      const data = await response.json();
      if (data.streamUrl) {
        setStreamUrl(data.streamUrl);
      }
    } catch (err) {
      console.error("Streaming failed:", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-4xl mx-auto bg-black rounded-xl overflow-hidden shadow-2xl aspect-video">
      {!streamUrl ? (
        <div className="flex flex-col items-center justify-center h-full space-y-4">
          <button
            onClick={fetchStream}
            disabled={loading}
            className="px-8 py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-full font-bold transition-all transform hover:scale-105"
          >
            {loading ? "Decrypting Stream..." : "▶ Watch Now (Ad-Free)"}
          </button>
        </div>
      ) : (
        <ReactPlayer
          url={streamUrl}
          controls
          playing
          width="100%"
          height="100%"
          config={{ file: { forceHLS: true } }}
        />
      )}
    </div>
  );
};

export default MovieStreamer;
