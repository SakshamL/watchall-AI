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

const BACKDROP_PATH = "https://image.tmdb.org/t/p/w780";

function HomeWideCard({ item, mediaType = "movie", badge = "", emphasis = "" }) {
  const title = titleOf(item);
  const year = String(item?.release_date || item?.first_air_date || "").slice(
    0,
    4,
  );
  const rating = ratingLabel(item?.vote_average);
  const language = languageLabel(item?.original_language);
  const image = item?.backdrop_path
    ? BACKDROP_PATH + item.backdrop_path
    : item?.poster_path
      ? BACKDROP_PATH + item.poster_path
      : "/no-poster.jpg";
  const overview = String(item?.overview || "").trim();
  const scoreCard = emphasis === "score";
  const prefetchRef = useStreamPrefetch(
    item?.id,
    mediaType,
    item?.original_language
  );

  return (
    <Link
      to={linkFor(item, mediaType)}
      ref={prefetchRef}
      title={title}
      className="group/card block w-full rounded-[14px] focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/80 focus-visible:ring-offset-2 focus-visible:ring-offset-black"
    >
      <div className="relative aspect-video w-full overflow-hidden rounded-[12px] border border-white/5 bg-[#101528] shadow-[0_8px_22px_rgba(0,0,0,0.45)] transition duration-200 ease-out group-hover/card:-translate-y-1 group-hover/card:border-white/20 group-hover/card:shadow-[0_18px_40px_rgba(0,0,0,0.65)]">
        <img
          src={image}
          alt={title}
          loading="lazy"
          decoding="async"
          className="img-fade-in h-full w-full object-cover transition duration-500 ease-out group-hover/card:scale-[1.04]"
          onLoad={(e) => e.target.classList.add("loaded")}
          onError={(e) => {
            e.target.onerror = null;
            e.target.src = "/no-poster.jpg";
          }}
        />

        <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black via-black/30 to-transparent" />
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-r from-black/45 via-transparent to-transparent" />

        {badge ? (
          <span className="absolute left-3 top-3 rounded-md bg-black/60 px-2 py-0.5 text-[9px] font-bold uppercase tracking-[0.18em] text-cyan-200 ring-1 ring-cyan-300/25 backdrop-blur">
            {badge}
          </span>
        ) : null}

        {rating ? (
          <span
            className={`absolute right-3 top-3 flex items-center gap-1 rounded-md bg-black/65 font-bold text-amber-300 ring-1 ring-white/10 backdrop-blur ${
              scoreCard
                ? "px-2 py-1 text-[13px] shadow-[0_4px_14px_rgba(0,0,0,0.55)]"
                : "px-1.5 py-0.5 text-[10px]"
            }`}
          >
            <FaStar size={scoreCard ? 10 : 8} />
            {rating}
            {scoreCard ? (
              <span className="ml-0.5 text-[8px] font-semibold uppercase tracking-wider text-white/45">
                TMDB
              </span>
            ) : null}
          </span>
        ) : null}

        <span className="absolute inset-0 flex items-center justify-center opacity-0 transition duration-200 group-hover/card:opacity-100">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-white/90 text-black shadow-[0_6px_22px_rgba(0,0,0,0.65)]">
            <FaPlay size={15} />
          </span>
        </span>

        <div className="absolute inset-x-0 bottom-0 p-3.5">
          <p className="line-clamp-1 text-[14px] font-bold leading-snug text-white [text-shadow:_0_1px_4px_rgba(0,0,0,0.9)]">
            {title}
          </p>
          <p className="mt-1 flex flex-wrap items-center gap-x-1.5 text-[9.5px] font-semibold uppercase tracking-[0.14em] text-white/60">
            {year ? <span>{year}</span> : null}
            {language ? <span>• {language}</span> : null}
            {mediaType === "tv" ? <span>• Series</span> : null}
          </p>
          {overview ? (
            <p className="mt-1 line-clamp-2 max-h-0 overflow-hidden text-[11px] leading-snug text-white/70 opacity-0 transition-all duration-300 group-hover/card:mt-1.5 group-hover/card:max-h-10 group-hover/card:opacity-100">
              {overview}
            </p>
          ) : null}
        </div>
      </div>
    </Link>
  );
}

export default HomeWideCard;
