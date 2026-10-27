import { useRef, useState } from "react";
import { clamp, formatTime } from "./playerUtils";

export default function PlayerTimeline({
  currentTime,
  duration,
  buffered,
  isLive,
  onSeek,
  onSeekBy,
}) {
  const trackRef = useRef(null);
  const [dragValue, setDragValue] = useState(null);
  const [hoverValue, setHoverValue] = useState(null);
  const safeDuration = Math.max(0.1, Number(duration) || 0);
  const shown = dragValue ?? currentTime;
  const percent = clamp((shown / safeDuration) * 100, 0, 100);
  const bufferedPercent = clamp((buffered / safeDuration) * 100, 0, 100);
  const previewValue = dragValue ?? hoverValue;
  const previewPercent = clamp((previewValue ?? 0) / safeDuration * 100, 0, 100);

  const valueFromEvent = (event) => {
    const rect = trackRef.current?.getBoundingClientRect();
    if (!rect || rect.width <= 0) return 0;
    return clamp((event.clientX - rect.left) / rect.width, 0, 1) * safeDuration;
  };

  const handlePointerDown = (event) => {
    if (safeDuration <= 0) return;
    const next = valueFromEvent(event);
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setDragValue(next);
    setHoverValue(null);
    event.preventDefault();
  };

  const handlePointerMove = (event) => {
    const next = valueFromEvent(event);
    if (event.pointerType === "mouse") setHoverValue(next);
    if (dragValue !== null) setDragValue(next);
  };

  const commit = (event) => {
    if (dragValue === null) return;
    const next = valueFromEvent(event);
    setDragValue(null);
    setHoverValue(null);
    onSeek(next);
  };

  const handlePointerUp = (event) => {
    commit(event);
    event.currentTarget.releasePointerCapture?.(event.pointerId);
  };

  const handleKeyDown = (event) => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      event.stopPropagation();
      onSeekBy(-10);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      event.stopPropagation();
      onSeekBy(10);
    } else if (event.key === "Home") {
      event.preventDefault();
      event.stopPropagation();
      onSeek(0);
    } else if (event.key === "End") {
      event.preventDefault();
      event.stopPropagation();
      onSeek(safeDuration);
    } else if (event.key === "PageUp") {
      event.preventDefault();
      event.stopPropagation();
      onSeekBy(60);
    } else if (event.key === "PageDown") {
      event.preventDefault();
      event.stopPropagation();
      onSeekBy(-60);
    }
  };

  return (
    <div className="wa-timeline-wrap">
      <div
        ref={trackRef}
        className="wa-timeline"
        role="slider"
        tabIndex={0}
        data-focusable="true"
        aria-label="Seek"
        aria-valuemin={0}
        aria-valuemax={Math.round(safeDuration)}
        aria-valuenow={Math.round(clamp(shown, 0, safeDuration))}
        aria-valuetext={
          isLive
            ? `Live window, ${formatTime(shown)}`
            : `${formatTime(shown)} of ${formatTime(safeDuration)}`
        }
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={() => setDragValue(null)}
        onPointerLeave={() => {
          if (dragValue === null) setHoverValue(null);
        }}
        onKeyDown={handleKeyDown}
      >
        <div className="wa-timeline-track" />
        <div className="wa-timeline-buffered" style={{ width: `${bufferedPercent}%` }} />
        <div className="wa-timeline-played" style={{ width: `${percent}%` }} />
        <div className="wa-timeline-thumb" style={{ left: `${percent}%` }} />
        {previewValue !== null && previewValue !== undefined ? (
          <div
            className="wa-timeline-tooltip"
            style={{ left: `${clamp(previewPercent, 6, 94)}%` }}
          >
            {formatTime(previewValue)}
          </div>
        ) : null}
      </div>
    </div>
  );
}
