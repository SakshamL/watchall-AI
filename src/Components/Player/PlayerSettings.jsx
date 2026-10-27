import { Check, Loader2, Server, X } from "lucide-react";
import { formatTime, qualityLabel } from "./playerUtils";

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];
const CAPTION_SIZES = [
  { id: "small", label: "Small" },
  { id: "default", label: "Default" },
  { id: "large", label: "Large" },
  { id: "xlarge", label: "Extra large" },
];

function Section({ title, children }) {
  return (
    <section className="wa-sheet-section">
      <h3 className="wa-sheet-heading">{title}</h3>
      <div className="wa-sheet-list">{children}</div>
    </section>
  );
}

export default function PlayerSettings({
  snapshot,
  servers,
  serversReady,
  activeServer,
  switchingServer,
  onSelectServer,
  onClose,
  onOpenHelp,
  onAction,
  profile,
  captionSize,
  settingsRef,
}) {
  const qualities = [...(snapshot.qualities || [])].sort(
    (a, b) =>
      (b.height || b.bitrate || 0) - (a.height || a.bitrate || 0)
  );
  const currentQuality = qualities.find((item) => item.id === snapshot.quality);
  const bufferAhead = Math.max(0, snapshot.bufferedEnd - snapshot.currentTime);

  const handleActionClick = (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const action = target.getAttribute("data-action");
    const value = target.getAttribute("data-value");
    if (action === "server") {
      if (!switchingServer) {
        onSelectServer(value);
        onClose();
      }
      return;
    }
    onAction(action, value);
  };

  return (
    <div className="wa-sheet-layer">
      <button
        type="button"
        className="wa-sheet-backdrop"
        aria-label="Close settings"
        tabIndex={-1}
        onClick={onClose}
      />
      <div
        ref={settingsRef}
        className="wa-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="wa-settings-title"
        onClick={handleActionClick}
      >
        <div className="wa-sheet-drag" aria-hidden="true" />
        <div className="wa-sheet-header">
          <h2 id="wa-settings-title">Settings</h2>
          <button
            type="button"
            className="wa-icon-btn wa-sheet-close"
            onClick={onClose}
            aria-label="Close settings"
            data-focusable="true"
          >
            <X size={22} />
          </button>
        </div>

        <div className="wa-sheet-body">
          <Section title="Quality">
            <button
              type="button"
              className={`wa-option-row ${snapshot.quality === -1 ? "is-active" : ""}`}
              aria-pressed={snapshot.quality === -1}
              data-focusable="true"
              data-action="quality"
              data-value="-1"
            >
              <span>Auto</span>
              <span className="wa-option-meta">
                {snapshot.quality === -1 ? <Check size={18} /> : null}
              </span>
            </button>
            {qualities.map((quality) => (
              <button
                key={quality.id}
                type="button"
                className={`wa-option-row ${snapshot.quality === quality.id ? "is-active" : ""}`}
                data-focusable="true"
                data-action="quality"
                data-value={quality.id}
                aria-pressed={snapshot.quality === quality.id}
              >
                <span>{qualityLabel(quality)}</span>
                <span className="wa-option-meta">
                  {snapshot.quality === quality.id ? <Check size={18} /> : null}
                </span>
              </button>
            ))}
          </Section>

          <Section title="Playback speed">
            <div className="wa-chip-list">
              {SPEEDS.map((speed) => (
                <button
                  key={speed}
                  type="button"
                  className={`wa-chip ${snapshot.playbackRate === speed ? "is-active" : ""}`}
                  data-focusable="true"
                  data-action="rate"
                  data-value={speed}
                  aria-pressed={snapshot.playbackRate === speed}
                >
                  {speed}x
                </button>
              ))}
            </div>
          </Section>

          {snapshot.audioTracks.length > 0 ? (
            <Section title="Audio">
              {snapshot.audioTracks.map((track, index) => (
                <button
                  key={`${track.label}-${index}`}
                  type="button"
                  className={`wa-option-row ${snapshot.audioTrack === index ? "is-active" : ""}`}
                  data-focusable="true"
                  data-action="audio"
                  data-value={index}
                  aria-pressed={snapshot.audioTrack === index}
                >
                  <span>
                    {track.label || track.language || `Audio ${index + 1}`}
                    {track.language ? (
                      <span className="wa-option-sub">{track.language}</span>
                    ) : null}
                  </span>
                  <span className="wa-option-meta">
                    {snapshot.audioTrack === index ? <Check size={18} /> : null}
                  </span>
                </button>
              ))}
            </Section>
          ) : null}

          <Section title="Subtitles">
            <button
              type="button"
              className={`wa-option-row ${snapshot.textTrack === -1 ? "is-active" : ""}`}
              data-focusable="true"
              data-action="text"
              data-value="-1"
              aria-pressed={snapshot.textTrack === -1}
            >
              <span>Off</span>
              <span className="wa-option-meta">
                {snapshot.textTrack === -1 ? <Check size={18} /> : null}
              </span>
            </button>
            {snapshot.textTracks.map((track, index) => (
              <button
                key={`${track.label}-${index}`}
                type="button"
                className={`wa-option-row ${snapshot.textTrack === index ? "is-active" : ""}`}
                data-focusable="true"
                data-action="text"
                data-value={index}
                aria-pressed={snapshot.textTrack === index}
              >
                <span>
                  {track.label || track.language || `Subtitles ${index + 1}`}
                  {track.language ? (
                    <span className="wa-option-sub">{track.language}</span>
                  ) : null}
                </span>
                <span className="wa-option-meta">
                  {snapshot.textTrack === index ? <Check size={18} /> : null}
                </span>
              </button>
            ))}
            {snapshot.textTracks.length === 0 ? (
              <p className="wa-empty-row">No subtitles available for this stream.</p>
            ) : (
              <div className="wa-caption-size">
                <span>Caption size</span>
                <div className="wa-chip-list">
                  {CAPTION_SIZES.map((size) => (
                    <button
                      key={size.id}
                      type="button"
                      className={`wa-chip ${captionSize === size.id ? "is-active" : ""}`}
                      data-focusable="true"
                      data-action="caption-size"
                      data-value={size.id}
                      aria-pressed={captionSize === size.id}
                    >
                      {size.label}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </Section>

          <Section title="Stream source">
            {!serversReady ? (
              <p className="wa-empty-row">Loading servers…</p>
            ) : null}
            {serversReady && servers.length === 0 ? (
              <p className="wa-empty-row">No alternative servers are available.</p>
            ) : null}
            {servers.map((server) => {
              const isActive = server.name === activeServer;
              const isSwitching = switchingServer === server.name;
              return (
                <button
                  key={server.name}
                  type="button"
                  className={`wa-option-row wa-server-row ${isActive ? "is-active" : ""}`}
                  data-focusable="true"
                  data-action="server"
                  data-value={server.name}
                  disabled={!!switchingServer}
                  aria-pressed={isActive}
                >
                  <span className="wa-server-name">
                    <Server size={16} />
                    {server.name}
                    {server.language ? (
                      <span className="wa-option-sub">{server.language}</span>
                    ) : null}
                  </span>
                  <span className="wa-option-meta">
                    {server.mp4 ? <span className="wa-tag">MP4</span> : null}
                    {isSwitching ? (
                      <Loader2 size={16} className="wa-spin" />
                    ) : isActive ? (
                      <span className="wa-active-dot" aria-label="Active" />
                    ) : null}
                  </span>
                </button>
              );
            })}
          </Section>

          <Section title="Playback information">
            <dl className="wa-info-grid">
              <div>
                <dt>Engine</dt>
                <dd>{snapshot.engine ? snapshot.engine.toUpperCase() : "—"}</dd>
              </div>
              <div>
                <dt>Quality</dt>
                <dd>{snapshot.quality === -1 ? "Auto" : qualityLabel(currentQuality)}</dd>
              </div>
              <div>
                <dt>Buffer</dt>
                <dd>{formatTime(bufferAhead)}</dd>
              </div>
              <div>
                <dt>Server</dt>
                <dd>{activeServer || "—"}</dd>
              </div>
              <div>
                <dt>Live</dt>
                <dd>{snapshot.isLive ? "Yes" : "No"}</dd>
              </div>
              <div>
                <dt>Device</dt>
                <dd>{profile.isTV ? "TV" : profile.isMobile ? "Mobile" : profile.isTablet ? "Tablet" : "Desktop"}</dd>
              </div>
            </dl>
          </Section>

          <button
            type="button"
            className="wa-option-row wa-help-link"
            onClick={onOpenHelp}
            data-focusable="true"
          >
            <span>Keyboard shortcuts</span>
            <span className="wa-option-meta">?</span>
          </button>
        </div>
      </div>
    </div>
  );
}
