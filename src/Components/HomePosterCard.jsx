import React from "react";
import { Link } from "react-router-dom";
import { FaPlay, FaStar } from "react-icons/fa";
import {
  languageLabel,
  linkFor,
  ratingLabel,
  titleOf,
} from "../Services/mediaCards";
import { useStreamPrefetch } from "../Services/streamPrefetch";

const POSTER_PATH = "https://image.tmdb.org/t/p/w342";

function HomePosterCard({
  item,
  mediaType = "movie",
  rank = 0,
  rankBadge = 0,
  badge = "",
  progress = null,
  resumeAt = "",
  to = "",
}) {
  const title = titleOf(item);
  const year = String(item?.release_date || item?.first_air_date || "").slice(
    0,
    4,
  );
  const rating = ratingLabel(item?.vote_average);
  const language = languageLabel(item?.original_language);
  const poster = item?.poster_path
    ? POSTER_PATH + item.poster_path
    : "/no-poster.jpg";
  const percent = Number.isFinite(progress)
    ? Math.max(2, Math.min(100, Math.round(progress * 100)))
    : null;
  const prefetchRef = useStreamPrefetch(
    item?.id,
    mediaType,
    item?.original_language
  );

  const meta = [year, language, mediaType === "tv" ? "Series" : ""].filter(
    Boolean,
  );

  return (
    <Link
      to={to || linkFor(item, mediaType)}
      ref={prefetchRef}
      title={title}
      aria-label={rank ? `#${rank}: ${title}` : title}
      className="group/card flex w-full items-end rounded-[14px] focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/80 focus-visible:ring-offset-2 focus-visible:ring-offset-black"
    >
      {rank > 0 ? (
        <span
          className={`rank-numeral shrink-0 select-none ${
            rank >= 10 ? "rank-numeral-double" : ""
          }`}
          aria-hidden="true"
        >
          {rank}
        </span>
      ) : null}

      <div className="relative w-full min-w-0 overflow-hidden rounded-[12px] border border-white/5 bg-[#101528] shadow-[0_8px_20px_rgba(0,0,0,0.45)] transition duration-200 ease-out group-hover/card:-translate-y-1 group-hover/card:border-white/20 group-hover/card:shadow-[0_18px_38px_rgba(0,0,0,0.65)]">
        <div className="img-skeleton relative aspect-[2/3] w-full overflow-hidden rounded-[12px]">
          <img
            src={poster}
            alt={title}
            loading="lazy"
            decoding="async"
            className="img-fade-in h-full w-full object-cover transition duration-500 ease-out group-hover/card:scale-[1.05]"
            onLoad={(e) => e.target.classList.add("loaded")}
            onError={(e) => {
              e.target.onerror = null;
              e.target.src = "/no-poster.jpg";
            }}
          />

          <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black via-black/25 to-transparent opacity-95" />

          {rankBadge > 0 ? (
            <span className="absolute left-2 top-2 rounded-md bg-black/70 px-1.5 py-0.5 text-[10px] font-black tabular-nums text-amber-300 ring-1 ring-white/15 backdrop-blur">
              #{rankBadge}
            </span>
          ) : null}

          {badge ? (
            <span className="absolute left-2 top-2 rounded-md bg-black/65 px-2 py-0.5 text-[9px] font-bold uppercase tracking-[0.16em] text-cyan-200 ring-1 ring-cyan-300/25 backdrop-blur">
              {badge}
            </span>
          ) : null}

          {rating ? (
            <span className="absolute right-2 top-2 flex items-center gap-1 rounded-md bg-black/65 px-1.5 py-0.5 text-[10px] font-bold text-amber-300 ring-1 ring-white/10 backdrop-blur">
              <FaStar size={8} />
              {rating}
            </span>
          ) : null}

          {to ? (
            <span className="absolute inset-0 flex items-center justify-center opacity-0 transition duration-200 group-hover/card:opacity-100">
              <span className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 text-black shadow-[0_6px_20px_rgba(0,0,0,0.6)]">
                <FaPlay size={14} />
              </span>
            </span>
          ) : null}

          <div className="absolute inset-x-0 bottom-0 px-2.5 pb-3 pt-8">
            <p className="line-clamp-2 text-[12.5px] font-semibold leading-snug text-white [text-shadow:_0_1px_3px_rgba(0,0,0,0.9)]">
              {title}
            </p>
            {meta.length ? (
              <p className="mt-1 truncate text-[9.5px] font-medium uppercase tracking-[0.12em] text-white/60">
                {meta.join(" • ")}
              </p>
            ) : null}
            {resumeAt ? (
              <p className="mt-1 text-[10px] font-semibold text-cyan-300">
                Resume at {resumeAt}
              </p>
            ) : null}
          </div>

          {percent !== null ? (
            <div className="absolute inset-x-0 bottom-0 h-[3px] bg-white/15">
              <div
                className="h-full rounded-r-full bg-gradient-to-r from-cyan-400 to-indigo-500"
                style={{ width: `${percent}%` }}
              />
            </div>
          ) : null}
        </div>
      </div>
    </Link>
  );
}

export default HomePosterCard;
