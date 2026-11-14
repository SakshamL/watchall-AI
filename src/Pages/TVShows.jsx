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

const Dot = () => <span className="mx-1 inline-block h-1.5 w-1.5 rounded-full bg-white/40" />;

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
    <section className="mt-8">
      <div className="mb-3">
        <h2 className="text-base font-bold sm:text-lg">{title}</h2>
        {eyebrow ? (
          <p className="mt-0.5 text-xs text-white/45 sm:text-sm">{eyebrow}</p>
        ) : null}
      </div>

      {loading ? (
        <div className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 sm:gap-3 md:grid-cols-5 lg:grid-cols-6">
          {Array.from({ length: 12 }).map((_, index) => (
            <div
              key={`skeleton-${index}`}
              className="img-skeleton aspect-[2/3] w-full rounded-lg"
            />
          ))}
        </div>
      ) : shows.length ? (
        <div className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 sm:gap-3 md:grid-cols-5 lg:grid-cols-6">
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
        <div className="mx-auto w-full max-w-[1320px] pb-16">
          <div className="mt-6">
            <SearchBar />
          </div>

          <div className="mt-8">
            <h1 className="text-xl font-black sm:text-2xl">TV Shows</h1>
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

            {/* Poster + info */}
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
                  {tvDetails.tagline ? (
                    <p className="mt-1 text-sm italic text-white/60 sm:text-base">
                      {tvDetails.tagline}
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
                    {cert ? (
                      <>
                        <span className="rounded border border-white/20 px-1.5 py-0.5 text-[10px] font-semibold text-white/80">
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
                        <span>{runtime} / ep</span>
                      </>
                    ) : null}
                  </div>

                  {Array.isArray(tvDetails.genres) &&
                  tvDetails.genres.length ? (
                    <div className="mt-3 flex flex-wrap gap-1.5">
                      {tvDetails.genres.map((genre) => (
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
              </div>

              {/* Season and episode picker */}
              <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3 py-2.5">
                <div className="flex items-center gap-2">
                  <label htmlFor="tv-season" className="text-xs text-white/50">
                    Season
                  </label>
                  <select
                    id="tv-season"
                    value={seasonNumber}
                    onChange={(event) =>
                      setSeasonNumber(Number(event.target.value))
                    }
                    className="rounded-md border border-white/10 bg-[#11131e] px-2.5 py-1.5 text-xs text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/70"
                  >
                    {seasons.map((season) => (
                      <option
                        key={season.id ?? season.season_number}
                        value={Number(season.season_number)}
                      >
                        {season.season_number}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex items-center gap-2">
                  <label htmlFor="tv-episode" className="text-xs text-white/50">
                    Episode
                  </label>
                  <select
                    id="tv-episode"
                    value={episodeNumber}
                    onChange={(event) =>
                      setEpisodeNumber(Number(event.target.value))
                    }
                    className="rounded-md border border-white/10 bg-[#11131e] px-2.5 py-1.5 text-xs text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/70"
                  >
                    {Array.from({ length: episodeCount }).map((_, index) => (
                      <option key={index + 1} value={index + 1}>
                        {index + 1}
                      </option>
                    ))}
                  </select>
                </div>

                <span className="text-[11px] text-white/40">
                  {episodeCount} {episodeCount === 1 ? "episode" : "episodes"}
                </span>
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
                    {tvDetails.overview ||
                      "No synopsis is available for this show yet."}
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
