import React, { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { movieAPI } from "../Services/api";
import SearchBar from "../Components/SearchBar";
import CastCard from "../Components/CastCard";
import HomePosterCard from "../Components/HomePosterCard";
import SourceSelectModal from "../Components/SourceSelectModal";
import {
  formatRuntime,
  pickTrailer,
  pickTvCertification,
} from "../Services/mediaDetails";
import { languageLabel } from "../Services/mediaCards";
import { Play, Film, Star, Circle, Summary } from "lucide-react";

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

const usableShows = (results) =>
  (Array.isArray(results) ? results : []).filter(
    (item) => item && item.id != null && !item.adult && item.poster_path,
  );

const normalSeasons = (details) =>
  Array.isArray(details?.seasons)
    ? details.seasons.filter((season) => Number(season.season_number) > 0)
    : [];

/* -------------------------------------------------------------------------- */
/* Browse: /tv                                                                */
/* -------------------------------------------------------------------------- */

function TvSection({ title, eyebrow, shows, loading }) {
  return (
    <section className="mt-10">
      <div className="mb-4">
        <h2 className="text-xl font-bold sm:text-2xl">{title}</h2>
        {eyebrow ? (
          <p className="mt-1 text-sm text-white/45">{eyebrow}</p>
        ) : null}
      </div>

      {loading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 lg:grid-cols-6">
          {Array.from({ length: 12 }).map((_, index) => (
            <div
              key={`skeleton-${index}`}
              className="img-skeleton aspect-[2/3] w-full rounded-[12px]"
            />
          ))}
        </div>
      ) : shows.length ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 lg:grid-cols-6">
          {shows.slice(0, 18).map((show) => (
            <HomePosterCard key={show.id} item={show} mediaType="tv" />
          ))}
        </div>
      ) : (
        <p className="rounded-xl border border-white/10 bg-white/[0.03] px-4 py-6 text-sm text-white/45">
          These shows could not be loaded right now. Please try again in a
          moment.
        </p>
      )}
    </section>
  );
}

function TvBrowse() {
  const [sections, setSections] = useState({ trending: [], topRated: [] });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;

    Promise.all([
      movieAPI.getTrendingTvShows(1),
      movieAPI.getTopRatedTvShows(1),
    ])
      .then(([trending, topRated]) => {
        if (!alive) return;
        setSections({
          trending: usableShows(trending?.results),
          topRated: usableShows(topRated?.results),
        });
      })
      .catch(() => {
        if (alive) setSections({ trending: [], topRated: [] });
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, []);

  return (
    <>
      <title>{"TV Shows - WatchAll"}</title>
      <div className="w-full text-white">
        <div className="mx-auto w-full max-w-[1400px] pb-16">
          <div className="mt-8">
            <SearchBar />
          </div>

          <div className="mt-10">
            <h1 className="text-2xl font-black sm:text-3xl">TV Shows</h1>
            <p className="mt-1 text-sm text-white/45">
              Trending series and the highest rated shows of all time.
            </p>
          </div>

          <TvSection
            title="Trending This Week"
            eyebrow="What everyone is bingeing right now"
            shows={sections.trending}
            loading={loading}
          />

          <TvSection
            title="Top Rated Series"
            eyebrow="The best of television, filtered by real audience votes"
            shows={sections.topRated}
            loading={loading}
          />
        </div>
      </div>
    </>
  );
}

/* -------------------------------------------------------------------------- */
/* Details: /tv/:id                                                           */
/* -------------------------------------------------------------------------- */

function TvDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [tvDetails, setTvDetails] = useState(null);
  const [cast, setCast] = useState([]);
  const [cert, setCert] = useState("");
  const [trailer, setTrailer] = useState("");
  const [seasonNumber, setSeasonNumber] = useState(1);
  const [episodeNumber, setEpisodeNumber] = useState(1);
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

      setTvDetails(null);
      setCast([]);
      setCert("");
      setTrailer("");

      const [details, credits, ratings, videos] = await Promise.all([
        movieAPI.getTvDetails(id),
        movieAPI.getTvCast(id),
        movieAPI.getTvCert(id),
        movieAPI.getTvVideos(id),
      ]);

      if (!alive) return;
      setTvDetails(details || null);
      setCast(Array.isArray(credits?.cast) ? credits.cast : []);
      setCert(pickTvCertification(ratings?.results));
      setTrailer(pickTrailer(videos?.results));

      const seasons = normalSeasons(details);
      setSeasonNumber(
        seasons.length ? Number(seasons[0].season_number) || 1 : 1,
      );
      setEpisodeNumber(1);
    };

    load();
    return () => {
      alive = false;
    };
  }, [id]);

  // A new season starts at its first episode.
  useEffect(() => {
    setEpisodeNumber(1);
  }, [seasonNumber]);

  const ready = Boolean(tvDetails?.id);
  const seasons = normalSeasons(tvDetails);
  const currentSeason =
    seasons.find((season) => Number(season.season_number) === seasonNumber) ||
    seasons[0] ||
    null;
  const episodeCount = Math.max(1, Number(currentSeason?.episode_count) || 1);
  const totalEpisodes = seasons.reduce(
    (total, season) => total + (Number(season.episode_count) || 0),
    0,
  );

  const title = tvDetails?.name || "TV Show";
  const rating = Number(tvDetails?.vote_average);
  const year = tvDetails?.first_air_date
    ? tvDetails.first_air_date.slice(0, 4)
    : "";
  const runtime = formatRuntime(
    tvDetails?.episode_run_time?.[0] || tvDetails?.last_episode_to_air?.runtime,
  );
  const backdrop = tvDetails?.backdrop_path
    ? IMGPATH + tvDetails.backdrop_path
    : "/no-poster.jpg";
  const poster = tvDetails?.poster_path
    ? IMGPATH + tvDetails.poster_path
    : "/placeholder.png";

  const detailRows = [
    { label: "Status", value: tvDetails?.status },
    { label: "First aired", value: tvDetails?.first_air_date || "" },
    {
      label: "Seasons",
      value: seasons.length ? String(seasons.length) : "",
    },
    {
      label: "Episodes",
      value: totalEpisodes ? String(totalEpisodes) : "",
    },
    { label: "Episode length", value: runtime },
    { label: "Language", value: languageLabel(tvDetails?.original_language) },
    { label: "Certification", value: cert },
    {
      label: "Networks",
      value: Array.isArray(tvDetails?.networks)
        ? tvDetails.networks
            .map((network) => network.name)
            .filter(Boolean)
            .join(", ")
        : "",
    },
  ];

  const openPlayer = (source) => {
    setSourceOpen(false);
    const params = new URLSearchParams({
      season: String(seasonNumber),
      episode: String(episodeNumber),
    });
    if (source === "vidnest") params.set("source", "2");
    else if (source === "netnaija") params.set("source", "3");
    navigate(`/tv/${tvDetails?.id || id}/watch?${params.toString()}`);
  };

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
                    {tvDetails.tagline ? (
                      <p className="mt-1 hidden text-sm italic text-white/65 sm:block md:text-base">
                        {tvDetails.tagline}
                      </p>
                    ) : null}

                    <div className="mt-3 flex flex-wrap items-center justify-center gap-x-3 gap-y-2 text-sm text-white/80 sm:justify-start md:text-[15px]">
                      {Number.isFinite(rating) && rating > 0 ? (
                        <>
                          <span className="flex items-center gap-1.5">
                            <Star
                              size={15}
                              className="fill-yellow-400 text-yellow-400"
                            />
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
                      {cert ? (
                        <>
                          <span className="rounded border border-white/20 px-1.5 py-0.5 text-[11px] font-semibold text-white/80">
                            {cert}
                          </span>
                          <Dot />
                        </>
                      ) : null}
                      {seasons.length ? (
                        <span>
                          {seasons.length}{" "}
                          {seasons.length === 1 ? "Season" : "Seasons"}
                        </span>
                      ) : null}
                      {runtime ? (
                        <>
                          <Dot />
                          <span>{runtime} / episode</span>
                        </>
                      ) : null}
                    </div>

                    {Array.isArray(tvDetails.genres) &&
                    tvDetails.genres.length ? (
                      <div className="mt-3 flex flex-wrap justify-center gap-2 sm:justify-start">
                        {tvDetails.genres.map((genre) => (
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
                </div>

                {/* Season and episode picker for playback */}
                <div className="mt-4 flex flex-wrap items-center justify-center gap-3 rounded-xl border border-white/10 bg-[#0b1020]/80 px-3 py-3 sm:justify-start">
                  <div className="flex items-center gap-2">
                    <label
                      htmlFor="tv-season"
                      className="text-sm text-white/50"
                    >
                      Season
                    </label>
                    <select
                      id="tv-season"
                      value={seasonNumber}
                      onChange={(event) =>
                        setSeasonNumber(Number(event.target.value))
                      }
                      className="rounded-lg border border-white/10 bg-[#11131e] px-3 py-1.5 text-sm text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/70"
                    >
                      {seasons.map((season) => (
                        <option
                          key={season.id ?? season.season_number}
                          value={Number(season.season_number)}
                        >
                          Season {season.season_number}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="flex items-center gap-2">
                    <label
                      htmlFor="tv-episode"
                      className="text-sm text-white/50"
                    >
                      Episode
                    </label>
                    <select
                      id="tv-episode"
                      value={episodeNumber}
                      onChange={(event) =>
                        setEpisodeNumber(Number(event.target.value))
                      }
                      className="rounded-lg border border-white/10 bg-[#11131e] px-3 py-1.5 text-sm text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/70"
                    >
                      {Array.from({ length: episodeCount }).map((_, index) => (
                        <option key={index + 1} value={index + 1}>
                          Episode {index + 1}
                        </option>
                      ))}
                    </select>
                  </div>

                  <span className="text-xs text-white/40">
                    {episodeCount}{" "}
                    {episodeCount === 1 ? "episode" : "episodes"} in this season
                  </span>
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
                    {tvDetails.overview ||
                      "No synopsis is available for this show yet."}
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
        onSelect={openPlayer}
      />
    </>
  );
}

function TVShows() {
  const { id } = useParams();
  return id ? <TvDetails /> : <TvBrowse />;
}

export default TVShows;
