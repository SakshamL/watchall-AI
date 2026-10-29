import { useLayoutEffect, useRef } from "react";
import { ChevronRight, X } from "lucide-react";
import "./SourceSelectModal.css";

const SOURCES = [
  { id: "viduki", title: "Server List 1", hint: "Recommended" },
  { id: "vidnest", title: "Server List 2", hint: "Alternative" },
];

export default function SourceSelectModal({ open, onClose, onSelect }) {
  const dialogRef = useRef(null);
  const optionRefs = useRef([]);

  useLayoutEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    optionRefs.current[0]?.focus();

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  if (!open) return null;

  const focusables = () =>
    Array.from(
      dialogRef.current?.querySelectorAll('[data-modal-focus="true"]') || []
    ).filter((element) => !element.disabled);

  const handleKeyDown = (event) => {
    const items = focusables();
    if (!items.length) return;
    const index = items.indexOf(document.activeElement);

    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      onClose();
      return;
    }

    const moveTo = (nextIndex) => {
      event.preventDefault();
      const next = items[(nextIndex + items.length) % items.length];
      next?.focus();
    };

    if (event.key === "ArrowDown" || event.key === "ArrowRight") {
      moveTo(index + 1);
      return;
    }
    if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
      moveTo(index - 1);
      return;
    }
    if (event.key === "Home") {
      moveTo(0);
      return;
    }
    if (event.key === "End") {
      moveTo(items.length - 1);
      return;
    }
    if (event.key === "Tab") {
      if (!event.shiftKey && index === items.length - 1) {
        event.preventDefault();
        items[0]?.focus();
      } else if (event.shiftKey && index <= 0) {
        event.preventDefault();
        items[items.length - 1]?.focus();
      }
    }
  };

  return (
    <div className="wa-source-layer">
      <button
        type="button"
        className="wa-source-backdrop"
        aria-label="Close server list"
        tabIndex={-1}
        onClick={onClose}
      />
      <div
        ref={dialogRef}
        className="wa-source-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="wa-source-title"
        onKeyDown={handleKeyDown}
      >
        <div className="wa-source-header">
          <h2 id="wa-source-title">Choose a server list</h2>
          <button
            type="button"
            className="wa-source-close"
            onClick={onClose}
            aria-label="Close server list"
            data-modal-focus="true"
          >
            <X size={20} />
          </button>
        </div>

        <div className="wa-source-options">
          {SOURCES.map((source, index) => (
            <button
              key={source.id}
              type="button"
              ref={(element) => {
                optionRefs.current[index] = element;
              }}
              className="wa-source-card"
              onClick={() => onSelect(source.id)}
              aria-label={`${source.title}. ${source.hint}.`}
              data-modal-focus="true"
            >
              <span className="wa-source-index" aria-hidden="true">
                {index + 1}
              </span>
              <span className="wa-source-label">{source.title}</span>
              <span className="wa-source-hint">{source.hint}</span>
              <ChevronRight
                className="wa-source-arrow"
                size={20}
                aria-hidden="true"
              />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
