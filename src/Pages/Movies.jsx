import React from "react";
import { useState, useEffect, useRef } from "react";
import { useNavigate, useParams, Link } from "react-router-dom";
import { movieAPI } from "../Services/api";
import CastCard from "../Components/CastCard";
import AllMoviesPage from "./AllMoviesPage";
import SourceSelectModal from "../Components/SourceSelectModal";
import {
  formatRuntime,
  pickMovieCertification,
  pickTrailer,
} from "../Services/mediaDetails";
import { languageLabel } from "../Services/mediaCards";
import { Play, Film, Plus, Star, Circle, Summary } from "lucide-react";

const IMGPATH = "https://image.tmdb.org/t/p/w780"; // detail page shows large images, w780 is sharp enough

const Dot = () => (
  <span className="mx-1 inline-block h-1.5 w-1.5 rounded-full bg-white/40" />
);

const DetailRow = ({ label, value }) =>
  value ? (
    <div className="flex items-start justify-between gap-3 border-b border-white/5 py-2 last:border-0 last:pb-0">
      <dt className="shrink-0 text-[12px] uppercase tracking-wide text-white/40">
        {label}
      </dt>
      <dd className="text-right text-[13px] font-medium text-white/85">
        {value}
      </dd>
    </div>
  ) : null;

function Movies(props) {
  const { id } = useParams();
  const navigate = useNavigate();
  const [movieDetails, setMovieDetails] = useState(null);
  const [cast, setCast] = useState([]);
  const [movieCert, setMovieCert] = useState("");
  const [trailer, setTrailer] = useState("");
  const [sourceOpen, setSourceOpen] = useState(false);
  const playButtonRef = useRef(null);
  const sourceWasOpenRef = useRef(false);

  useEffect(() => {
    if (sourceOpen) {
      sourceWasOpenRef.current = true;
      return;
    }
    if (sourceWasOpenRef.current) {
      sourceWasOpenRef.current = false;
      playButtonRef.current?.focus();
    }
  }, [sourceOpen]);

  useEffect(() => {
    let alive = true;

    const load = async () => {
      if (!id) return;

      setMovieDetails(null);
      setCast([]);
      setMovieCert("");
      setTrailer("");

      const [details, credits, certifications, videos] = await Promise.all([
        movieAPI.getMovieDetails(id),
        movieAPI.getCast(id),
        movieAPI.getCert(id),
        movieAPI.getVideos(id),
      ]);

      if (!alive) return;
      setMovieDetails(details || null);
      setCast(Array.isArray(credits?.cast) ? credits.cast : []);
      setMovieCert(pickMovieCertification(certifications?.results));
      setTrailer(pickTrailer(videos?.results));
    };

    load();
    return () => {
      alive = false;
    };
  }, [id]);

  function Movie_details() {
    const ready = Boolean(movieDetails?.id);
    const title = movieDetails?.title || "Movie";
    const runtime = formatRuntime(movieDetails?.runtime);
    const rating = Number(movieDetails?.vote_average);
    const year = movieDetails?.release_date
      ? movieDetails.release_date.slice(0, 4)
      : "";
    const backdrop = movieDetails?.backdrop_path
      ? IMGPATH + movieDetails.backdrop_path
      : "/no-poster.jpg";
    const poster = movieDetails?.poster_path
      ? IMGPATH + movieDetails.poster_path
      : "/placeholder.png";

    const detailRows = [
      { label: "Status", value: movieDetails?.status },
      { label: "Release date", value: movieDetails?.release_date || "" },
      { label: "Runtime", value: runtime },
      { label: "Language", value: languageLabel(movieDetails?.original_language) },
      {
        label: "Rating",
        value: Number.isFinite(rating) && rating > 0 ? `${rating.toFixed(1)} / 10` : "",
      },
      { label: "Certification", value: movieCert },
      {
        label: "Budget",
        value: movieDetails?.budget
          ? `$${movieDetails.budget.toLocaleString("en-US")}`
          : "",
      },
      {
        label: "Revenue",
        value: movieDetails?.revenue
          ? `$${movieDetails.revenue.toLocaleString("en-US")}`
          : "",
      },
    ];

    return (
      <>
        <title>{title + " - WatchAll"}</title>

        <div className="mx-auto w-full max-w-[1320px] pb-16">
          {!ready ? (
            <div className="animate-pulse space-y-6">
              <div className="img-skeleton h-[220px] w-full rounded-2xl sm:h-[300px] md:h-[400px]" />
              <div className="flex gap-4">
                <div className="img-skeleton aspect-[2/3] w-[100px] shrink-0 rounded-xl sm:w-[150px]" />
                <div className="flex-1 space-y-3 pt-2">
                  <div className="img-skeleton h-8 w-2/3 rounded" />
                  <div className="img-skeleton h-4 w-1/3 rounded" />
                  <div className="img-skeleton h-10 w-full max-w-sm rounded" />
                </div>
              </div>
            </div>
          ) : (
            <>
              {/* Backdrop */}
              <div className="relative h-[220px] w-full overflow-hidden rounded-2xl sm:h-[300px] md:h-[400px]">
                <img
                  src={backdrop}
                  alt=""
                  loading="eager"
                  fetchPriority="high"
                  decoding="async"
                  className="h-full w-full object-cover object-[center_20%] img-fade-in"
                  onLoad={(e) => e.target.classList.add("loaded")}
                  onError={(e) => {
                    e.target.onerror = null;
                    e.target.src = "/no-poster.jpg";
                    e.target.classList.add("loaded");
                  }}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-[#060b16] via-[#060b16]/55 to-transparent" />
                <div className="absolute inset-0 bg-gradient-to-r from-[#060b16]/60 via-transparent to-transparent" />
              </div>

              {/* Poster + info, overlapping the backdrop */}
              <div className="relative z-10 -mt-16 px-1 sm:-mt-24 sm:px-2 md:-mt-32">
                <div className="flex items-start gap-4 sm:gap-6">
                  <div className="w-[100px] shrink-0 sm:w-[140px] md:w-[180px]">
                    <div className="img-skeleton aspect-[2/3] w-full overflow-hidden rounded-xl border border-[#3fb2d5]/60 shadow-[0_8px_24px_rgba(63,178,213,0.35)]">
                      <img
                        src={poster}
                        alt={title}
                        loading="eager"
                        decoding="async"
                        className="h-full w-full rounded-xl object-cover img-fade-in"
                        onLoad={(e) => e.target.classList.add("loaded")}
                        onError={(e) => {
                          e.target.onerror = null;
                          e.target.src = "/placeholder.png";
                          e.target.classList.add("loaded");
                        }}
                      />
                    </div>
                  </div>

                  <div className="min-w-0 flex-1 pt-1">
                    <h1 className="text-[1.5rem] font-extrabold leading-tight tracking-tight [text-shadow:_0_2px_12px_rgba(0,0,0,0.9)] sm:text-3xl md:text-4xl lg:text-5xl">
                      {title}
                    </h1>
                    {movieDetails.tagline ? (
                      <p className="mt-1 text-sm italic text-white/60 sm:text-base">
                        {movieDetails.tagline}
                      </p>
                    ) : null}

                    <div className="mt-2.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] text-white/75 sm:text-sm">
                      {Number.isFinite(rating) && rating > 0 ? (
                        <>
                          <span className="flex items-center gap-1">
                            <Star size={13} className="fill-yellow-400 text-yellow-400" />
                            {rating.toFixed(1)}
                          </span>
                          <Dot />
                        </>
                      ) : null}
                      {year ? (
                        <>
                          <span>{year}</span>
                          <Dot />
                        </>
                      ) : null}
                      {movieCert ? (
                        <>
                          <span className="rounded border border-white/20 px-1.5 py-0.5 text-[10px] font-semibold text-white/80">
                            {movieCert}
                          </span>
                          <Dot />
                        </>
                      ) : null}
                      {runtime ? <span>{runtime}</span> : null}
                    </div>

                    {Array.isArray(movieDetails.genres) &&
                    movieDetails.genres.length ? (
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        {movieDetails.genres.map((genre) => (
                          <span
                            key={genre.id}
                            className="rounded-md border border-[#4f75c157] bg-[#1b2028]/80 px-2.5 py-1 text-[11px] krub-regular text-white/80"
                          >
                            {genre.name}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </div>

                {/* Actions */}
                <div className="mt-5 flex flex-wrap items-center gap-2.5">
                  <button
                    ref={playButtonRef}
                    type="button"
                    onClick={() => setSourceOpen(true)}
                    className="rounded-lg text-left focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-[#060b16]"
                    aria-haspopup="dialog"
                    aria-expanded={sourceOpen}
                  >
                    <span className="flex items-center gap-2 rounded-lg bg-gradient-to-r from-indigo-700 to-cyan-500 px-5 py-2.5 text-sm font-bold uppercase tracking-wide text-white shadow-[0_4px_16px_rgba(40,90,216,0.4)] transition hover:brightness-110">
                      <Play size={16} />
                      Play Now
                    </span>
                  </button>

                  {trailer ? (
                    <Link
                      to={`https://www.youtube.com/watch?v=${trailer}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.04] px-4 py-2.5 text-sm font-medium text-white/85 transition hover:bg-white/[0.08] focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-[#060b16]"
                    >
                      <Film size={15} className="text-red-400" />
                      Trailer
                    </Link>
                  ) : null}

                  <button
                    type="button"
                    className="flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.04] px-4 py-2.5 text-sm font-medium text-white/85 transition hover:bg-white/[0.08] focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-[#060b16]"
                  >
                    <Plus size={15} />
                    Watchlist
                  </button>
                </div>
              </div>

            {/* Synopsis, cast and details */}
            <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_280px] lg:gap-8">
              <div className="min-w-0 space-y-6">
                <section className="rounded-xl border border-white/8 bg-white/[0.03] p-4 sm:p-5">
                  <h2 className="mb-2.5 flex items-center gap-2 text-base font-bold sm:text-lg">
                    <Summary size={16} className="text-fuchsia-400" />
                    Synopsis
                  </h2>
                  <p className="text-[14px] leading-relaxed text-white/70 sm:text-[15px]">
                    {movieDetails.overview ||
                      "No synopsis is available for this title yet."}
                  </p>
                </section>

                <section>
                  <div className="mb-3 flex items-center justify-between gap-4">
                    <h2 className="text-base font-bold sm:text-lg">Cast</h2>
                    {cast.length ? (
                      <span className="text-xs text-white/40">
                        {cast.length} members
                      </span>
                    ) : null}
                  </div>

                  {cast.length ? (
                    <div className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6">
                      {cast.slice(0, 6).map((casting) => (
                        <CastCard
                          key={casting.id}
                          image={
                            casting.profile_path
                              ? "https://image.tmdb.org/t/p/w185" +
                                casting.profile_path
                              : "/no-poster.jpg"
                          }
                          name={casting.name}
                          char={casting.character}
                        />
                      ))}
                    </div>
                  ) : (
                    <p className="text-sm text-white/40">
                      Cast information is not available.
                    </p>
                  )}
                </section>
              </div>

              <aside className="lg:sticky lg:top-6 lg:self-start">
                <div className="rounded-xl border border-white/8 bg-white/[0.03] p-4">
                  <h2 className="text-sm font-bold uppercase tracking-wide text-white/60">
                    Details
                  </h2>
                  <dl className="mt-2 text-sm">
                    {detailRows.map((row) => (
                      <DetailRow
                        key={row.label}
                        label={row.label}
                        value={row.value}
                      />
                    ))}
                  </dl>
                </div>
              </aside>
            </div>
          </>
        )}
        </div>

        <SourceSelectModal
          open={sourceOpen}
          onClose={() => setSourceOpen(false)}
          onSelect={(source) => {
            setSourceOpen(false);
            const watchPath = `/movie/${movieDetails?.id || id}/watch`;
            const sourceParam =
              source === "vidnest"
                ? "?source=2"
                : source === "netnaija"
                  ? "?source=3"
                  : "";
            navigate(`${watchPath}${sourceParam}`);
          }}
        />
      </>
    );
  }

  function All_Movies_list(props) {
    return <AllMoviesPage language={props.language} />;
  }

  return (
    <div className="w-full text-white">
      {id ? <Movie_details /> : <All_Movies_list language={props.language} />}
    </div>
  );
}

export default Movies;
