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
  <span className="relative flex items-center justify-center">
    <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
    <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
  </span>
);

const DetailRow = ({ label, value }) =>
  value ? (
    <div className="flex items-start justify-between gap-4 border-b border-white/5 py-2.5 last:border-0 last:pb-0">
      <dt className="shrink-0 text-white/45">{label}</dt>
      <dd className="text-right font-medium text-white/85">{value}</dd>
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
      {
        label: "Release date",
        value: movieDetails?.release_date || "",
      },
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

        <div className="mx-auto w-full max-w-[1400px] pb-16">
          {!ready ? (
            <div className="animate-pulse">
              <div className="img-skeleton h-[200px] w-full rounded-[20px] sm:h-[260px] md:h-[340px] lg:h-[420px]" />
              <div className="-mt-16 flex flex-col items-center gap-4 px-4 sm:-mt-24 sm:flex-row sm:items-start sm:px-6 md:-mt-32 md:px-8">
                <div className="img-skeleton aspect-[2/3] w-[124px] shrink-0 rounded-2xl sm:w-[150px] lg:w-[190px]" />
                <div className="w-full space-y-3 pt-2">
                  <div className="img-skeleton mx-auto h-9 w-2/3 rounded-lg sm:mx-0 sm:w-1/2" />
                  <div className="img-skeleton mx-auto h-4 w-1/3 rounded sm:mx-0" />
                  <div className="img-skeleton mx-auto h-10 w-full max-w-sm rounded-lg sm:mx-0" />
                </div>
              </div>
            </div>
          ) : (
            <>
              {/* Backdrop + title block */}
              <div className="relative overflow-hidden rounded-[20px] border border-white/5 bg-[#070b16] shadow-[0_24px_60px_rgba(0,0,0,0.45)]">
                <div className="relative h-[200px] w-full sm:h-[260px] md:h-[340px] lg:h-[420px]">
                  <img
                    src={backdrop}
                    alt=""
                    loading="eager"
                    fetchPriority="high"
                    decoding="async"
                    className="h-full w-full object-cover object-[center_22%] img-fade-in"
                    onLoad={(e) => e.target.classList.add("loaded")}
                    onError={(e) => {
                      e.target.onerror = null;
                      e.target.src = "/no-poster.jpg";
                      e.target.classList.add("loaded");
                    }}
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-[#070b16] via-[#070b16]/45 to-[#070b16]/5" />
                  <div className="absolute inset-0 bg-gradient-to-r from-[#070b16]/85 via-[#070b16]/25 to-transparent" />
                </div>

                <div className="relative z-10 -mt-16 px-4 pb-6 sm:-mt-24 sm:px-6 md:-mt-32 md:px-8">
                  <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start sm:gap-6">
                    <div className="w-[124px] shrink-0 sm:w-[150px] lg:w-[190px]">
                      <div className="img-skeleton aspect-[2/3] w-full overflow-hidden rounded-2xl border border-[#3fb2d5] shadow-[0_0_18px_rgba(63,178,213,0.45)]">
                        <img
                          src={poster}
                          alt={title}
                          loading="eager"
                          decoding="async"
                          className="h-full w-full rounded-2xl object-cover img-fade-in"
                          onLoad={(e) => e.target.classList.add("loaded")}
                          onError={(e) => {
                            e.target.onerror = null;
                            e.target.src = "/placeholder.png";
                            e.target.classList.add("loaded");
                          }}
                        />
                      </div>
                    </div>

                    <div className="w-full min-w-0 text-center sm:pt-1 sm:text-left">
                      <h1 className="text-[1.6rem] font-black leading-tight [text-shadow:_0_2px_10px_rgba(0,0,0,0.8)] sm:text-4xl lg:text-5xl">
                        {title}
                      </h1>
                      {movieDetails.tagline ? (
                        <p className="mt-1 hidden text-sm italic text-white/65 sm:block md:text-base">
                          {movieDetails.tagline}
                        </p>
                      ) : null}

                      <div className="mt-3 flex flex-wrap items-center justify-center gap-x-3 gap-y-2 text-sm text-white/80 sm:justify-start md:text-[15px]">
                        {Number.isFinite(rating) && rating > 0 ? (
                          <>
                            <span className="flex items-center gap-1.5">
                              <Star size={15} className="fill-yellow-400 text-yellow-400" />
                              {rating.toFixed(2)}
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
                            <span className="rounded border border-white/20 px-1.5 py-0.5 text-[11px] font-semibold text-white/80">
                              {movieCert}
                            </span>
                            <Dot />
                          </>
                        ) : null}
                        {runtime ? <span>{runtime}</span> : null}
                      </div>

                      {Array.isArray(movieDetails.genres) &&
                      movieDetails.genres.length ? (
                        <div className="mt-3 flex flex-wrap justify-center gap-2 sm:justify-start">
                          {movieDetails.genres.map((genre) => (
                            <span
                              key={genre.id}
                              className="rounded-[10px] border-2 border-[#4f75c157] bg-[#1b2028] px-3 py-1.5 text-[12px] krub-regular md:px-4 md:text-[13px]"
                            >
                              {genre.name}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="mt-5 grid grid-cols-2 gap-3 sm:flex sm:flex-wrap sm:items-center">
                    <button
                      ref={playButtonRef}
                      type="button"
                      onClick={() => setSourceOpen(true)}
                      className="col-span-2 rounded-[10px] text-left focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-black sm:col-span-1"
                      aria-haspopup="dialog"
                      aria-expanded={sourceOpen}
                    >
                      <span className="flex items-center justify-center gap-3 rounded-[10px] bg-gradient-to-r from-indigo-800 to-cyan-500 px-5 py-3 text-[#f5f5f5] uppercase font-bold shadow-[0px_0px_5px_0px_rgba(40,90,216,1)] transition duration-150 hover:brightness-110">
                        <Play size={18} />
                        Play Now
                      </span>
                    </button>

                    {trailer ? (
                      <Link
                        to={`https://www.youtube.com/watch?v=${trailer}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center justify-center gap-3 rounded-[10px] bg-[#11131e] px-5 py-3 shadow-[0px_0px_3px_0px_rgba(255,255,255,0.3)] transition duration-150 hover:bg-[#181c2c] focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-black"
                      >
                        <Film size={18} className="text-red-500" />
                        <span className="text-sm font-medium">Trailer</span>
                      </Link>
                    ) : (
                      <span className="flex items-center justify-center gap-3 rounded-[10px] bg-[#11131e]/50 px-5 py-3 text-white/35">
                        <Film size={18} />
                        <span className="text-sm font-medium">No trailer</span>
                      </span>
                    )}

                    <button
                      type="button"
                      className="flex items-center justify-center gap-3 rounded-[10px] bg-[#0a0f22d1] px-5 py-3 shadow-[0px_0px_3px_0px_rgba(255,255,255,0.3)] transition duration-150 hover:bg-[#141b33] focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-black"
                    >
                      <Plus size={18} />
                      <span className="text-sm font-medium">Watchlist</span>
                    </button>
                  </div>
                </div>
              </div>

              {/* Synopsis, cast and facts */}
              <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-8">
                <div className="min-w-0">
                  <section className="rounded-xl border border-[#2b417a69] bg-[#0a0f22d1] p-4 sm:p-5">
                    <h2 className="mb-3 flex items-center gap-2 text-lg font-bold sm:text-xl">
                      <Summary color="magenta" />
                      Synopsis
                    </h2>
                    <p className="text-[15px] leading-relaxed text-white/70 sm:text-base">
                      {movieDetails.overview ||
                        "No synopsis is available for this title yet."}
                    </p>
                  </section>

                  <section className="mt-8">
                    <div className="mb-4 flex items-center justify-between gap-4">
                      <h2 className="text-xl font-bold">Cast</h2>
                      {cast.length ? (
                        <span className="text-sm text-white/40">
                          {cast.length} members
                        </span>
                      ) : null}
                    </div>

                    {cast.length ? (
                      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
                        {cast.slice(0, 12).map((casting) => (
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
                  <div className="rounded-xl border border-[#2b417a69] bg-[#0a0f22d1] p-5">
                    <h2 className="text-lg font-bold">Details</h2>
                    <dl className="mt-3 text-sm">
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
