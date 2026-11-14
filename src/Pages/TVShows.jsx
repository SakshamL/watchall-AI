import React from "react";
import { useState, useEffect, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { movieAPI } from "../Services/api";
import { tv_details_1, tv_details_2 } from "../Services/api";
import { castTv_api_1, castTv_api_2 } from "../Services/api";
import SearchBar from "../Components/SearchBar";
import { Link } from "react-router-dom";
import CastCard from "../Components/CastCard";
import SourceSelectModal from "../Components/SourceSelectModal";
import { Play } from "lucide-react";
import { Film } from "lucide-react";
import { Plus } from "lucide-react";
import { Star } from "lucide-react";
import { Circle } from "lucide-react";
import { Summary } from "lucide-react";

const IMGPATH = "https://image.tmdb.org/t/p/w780"; // detail page shows large images, w780 is sharp enough

function TVShows() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [tvDetails, setTvDetails] = useState([]);
  const [cast, setCast] = useState([]);
  const [tvCert, setTvCert] = useState("");
  const [trailer, setTrailer] = useState([]);
  const [sourceOpen, setSourceOpen] = useState(false);
  const [seasonNumber, setSeasonNumber] = useState(1);
  const [episodeNumber, setEpisodeNumber] = useState(1);
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
    if (!id) return;
    getTv_details();
    getcast_details();
    get_cert();
    get_trailer();
  }, []);

  useEffect(() => {
    if (!id) return;
    getTv_details();
    getcast_details();
    get_cert();
    get_trailer();
  }, [id]);

  const getTv_details = async () => {
    if (id) {
      const response = await fetch(tv_details_1 + id + tv_details_2);
      const responseJSON = await response.json();
      setTvDetails(responseJSON);
    }
  };

  const getcast_details = async () => {
    if (id) {
      const response = await fetch(castTv_api_1 + id + castTv_api_2);
      const responseJSON = await response.json();
      setCast(responseJSON.cast);
    }
  };

  const get_cert = async () => {
    if (id) {
      try {
        const response = await movieAPI.getTvCert(id);
        const results = response?.results || [];
        const pick = (region) =>
          results.find((r) => r.iso_3166_1 === region && r.rating);
        const entry = pick("IN") || pick("US") || results.find((r) => r.rating);
        setTvCert(entry?.rating || "");
      } catch (e) {
        setTvCert("");
      }
    }
  };

  const get_trailer = async () => {
    if (id) {
      try {
        const response = await movieAPI.getTvVideos(id);
        const results = response?.results || [];
        const youtube = results.filter(
          (v) => v.site === "YouTube" && v.key,
        );
        const official = youtube.find(
          (v) => v.type === "Trailer" && v.official,
        );
        const trailer = youtube.find((v) => v.type === "Trailer");
        setTrailer((official || trailer || youtube[0])?.key || "");
      } catch (e) {
        setTrailer("");
      }
    }
  };

  // Reset episode when season changes
  useEffect(() => {
    setEpisodeNumber(1);
  }, [seasonNumber]);

  const seasons = Array.isArray(tvDetails?.seasons)
    ? tvDetails.seasons.filter((s) => Number(s.season_number) > 0)
    : [];
  const currentSeason =
    seasons.find((s) => Number(s.season_number) === seasonNumber) ||
    seasons[0] ||
    null;
  const episodeCount = Math.max(1, Number(currentSeason?.episode_count) || 1);

  // Default season once details load
  useEffect(() => {
    if (seasons.length && seasonNumber === 1 && Number(seasons[0]?.season_number) !== 1) {
      setSeasonNumber(Number(seasons[0].season_number) || 1);
    }
  }, [tvDetails]);

  function Tv_details() {
    const runtimeMinutes = tvDetails?.episode_run_time?.[0] || 0;
    const runtime =
      Math.trunc(runtimeMinutes / 60).toString() +
      "h " +
      ((runtimeMinutes / 60) % 1).toString().substring(2, 3) * 6 +
      "m" +
      " ";

    return (
      <>
        <title>{(tvDetails?.name || "TV Show") + " - WatchAll"}</title>
        <div className="text-white flex flex-col w-full">
          {/* <SearchBar /> */}
          <div className="relative -mt-6">
            <div className="relative w-screen overflow-hidden left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] h-[56vw] max-h-[500px] rounded-none backdrop-mask-gradient img-skeleton">
              <img
                src={IMGPATH + tvDetails?.backdrop_path}
                alt=""
                loading="eager"
                fetchPriority="high"
                decoding="async"
                className="w-full h-full object-cover object-[center_15%] brightness-[1.1] img-fade-in"
                onLoad={(e) => e.target.classList.add("loaded")}
              />
            </div>

            <div className="flex justify-center">
              <div className="absolute top-[15%] w-[90%] brightness-[1]">
                <div className="text-white md:ml-[10px] mt-35 md:mt-50">
                  <div className="flex lg:block">
                    <div className="mb-3 w-[40%] flex-4 -ml-2">
                      <div className="img-skeleton lg:hidden w-50 h-[300px] md:w-60 md:h-[360px] rounded-2xl">
                        <img
                          src={IMGPATH + tvDetails?.poster_path}
                          alt={tvDetails?.name}
                          loading="eager"
                          decoding="async"
                          className="w-full h-full object-cover rounded-2xl border-[1px] border-[#3fb2d5] shadow-[0px_0px_7px_0px_rgba(63,178,213,1)] img-fade-in"
                          onLoad={(e) => e.target.classList.add("loaded")}
                          onError={(e) => { e.target.src = "/placeholder.png"; }}
                        />
                      </div>
                    </div>
                    <div className="flex-5">
                      <h2 className="lg:-mt-25 ml-3 md:ml-0 lg:ml-0 text-[110%] lg:text-start font-bold mb-3 md:text-4xl md:font-bold md:mb-4 [text-shadow:_0px_0px_5px_#000000]">
                        {tvDetails?.name}
                      </h2>
                      <h3 className="hidden md:block italic text-lg text-[#d8d8d8] font-semibold -mt-2">
                        {tvDetails?.tagline}
                      </h3>

                      <div className="grid md:grid-cols-2 lg:grid-cols-[1fr_auto_1fr] lg:w-[42%] max-sm:gap-3 sm:gap-4 mb-5 ml-3 md:ml-0 mt-4 md:mt-5 lg:mt-7">
                        <button
                          ref={playButtonRef}
                          type="button"
                          onClick={() => setSourceOpen(true)}
                          className="md:flex-1 text-left rounded-[10px] focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-black"
                          aria-haspopup="dialog"
                          aria-expanded={sourceOpen}
                        >
                          <span className="flex items-center gap-3 bg-gradient-to-r from-indigo-800 to-cyan-500 rounded-[10px] cursor-pointer px-4 py-2 text-[#f5f5f5] md:py-3 shadow-[0px_0px_5px_0px_rgba(40,_90,_216,_1)] uppercase font-bold hover:scale-[1.1] duration-100">
                            <Play />
                            PLAY NOW
                          </span>
                        </button>

                        {trailer ? (
                          <Link
                            to={`https://www.youtube.com/watch?v=${trailer}`}
                            className="md:flex-1"
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <div
                              className="flex gap-3 item-center lg:w-fit bg-[#11131e] rounded-[10px] cursor-pointer px-5 py-2 md:py-3 shadow-[0px_0px_3px_0px_rgba(255,_255,_255,_0.3)] font-normal"
                              onClick={() => {}}
                            >
                              <Film color="red" />
                              <p>Trailer</p>
                            </div>
                          </Link>
                        ) : null}
                        <div
                          className="flex gap-3 items-center md:col-span-2 lg:col-span-1 lg:w-fit bg-[#0a0f22d1] rounded-[10px] cursor-pointer px-5 py-2 md:py-3 shadow-[0px_0px_3px_0px_rgba(255,_255,_255,_0.3)] font-normal max-lg:text-sm"
                          onClick={() => {}}
                        >
                          <Plus />
                          Watchlist
                        </div>
                      </div>

                      {/* Season & episode picker for playback */}
                      <div className="flex flex-wrap items-center gap-3 mt-2 mb-3 ml-3 md:ml-0 krub-regular text-[14px]">
                        <div className="flex items-center gap-2">
                          <span className="text-white/60">Season</span>
                          <select
                            value={seasonNumber}
                            onChange={(e) => setSeasonNumber(Number(e.target.value))}
                            className="bg-[#11131e] border border-white/10 rounded-[10px] px-3 py-1.5 text-white focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70"
                          >
                            {(seasons.length ? seasons : [{ season_number: 1, episode_count: 1 }]).map((s) => (
                              <option key={s.season_number} value={Number(s.season_number)}>
                                {s.season_number}
                              </option>
                            ))}
                          </select>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-white/60">Episode</span>
                          <select
                            value={episodeNumber}
                            onChange={(e) => setEpisodeNumber(Number(e.target.value))}
                            className="bg-[#11131e] border border-white/10 rounded-[10px] px-3 py-1.5 text-white focus:outline-none focus-visible:ring-4 focus-visible:ring-cyan-300/70"
                          >
                            {Array.from({ length: episodeCount }).map((_, i) => (
                              <option key={i + 1} value={i + 1}>
                                {i + 1}
                              </option>
                            ))}
                          </select>
                        </div>
                      </div>

                      {/* ----------------------------------------------- */}
                      <div className="max-md:hidden lg:w-1/3 flex justify-evenly mt-5 mb-5 -ml-2 -mr-3 krub-regular text-[16px] md:text-[18px] flex-nowrap gap-1.5 items-center lg:ml-0 lg:justify-evenly">
                        <div className="flex items-center gap-1 text-[16px]">
                          <Star size={16} color="yellow" />
                          {tvDetails?.vote_average
                            ? tvDetails.vote_average.toFixed(2)
                            : null}

                          {/* <img
                        src="/star.png"
                        alt="star"
                        className="w-[14px] md:w-[20px] h-full -mt-[3px]"
                      /> */}
                        </div>
                        <div className="relative flex items-center justify-center mx-1">
                          <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                          <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                        </div>
                        <h2>
                          {tvDetails?.first_air_date
                            ? tvDetails.first_air_date.substring(0, 4)
                            : null}
                        </h2>
                        <div className="relative flex items-center justify-center mx-1">
                          <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                          <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                        </div>
                        <div className="text-xl flex items-center gap-2 text-[grey]">
                          [{" "}
                          <span className="text-[12px] text-white">
                            {tvCert}
                          </span>{" "}
                          ]
                        </div>
                        <div className="relative flex items-center justify-center mx-1">
                          <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                          <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                        </div>
                        <h2>
                          {tvDetails?.number_of_seasons
                            ? tvDetails.number_of_seasons +
                              (tvDetails.number_of_seasons === 1
                                ? " Season"
                                : " Seasons")
                            : null}
                        </h2>
                        <div className="relative flex items-center justify-center mx-1">
                          <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                          <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                        </div>
                        <h2>
                          {runtimeMinutes > 0 ? runtime : null}
                        </h2>
                      </div>
                      <div className="hidden md:flex flex-wrap justify-center lg:justify-start gap-2 krub-regular max-sm:text-[12px]/2.5 md:text-[13px] mt-3 ml-3 lg:ml-0">
                        {tvDetails?.genres
                          ? tvDetails.genres.map((genre) => {
                              return (
                                <Link key={genre.id}>
                                  <p className="bg-[#1b2028] border-[2px] border-[#4f75c157] py-2 px-3 mt-1 md:px-4 lg:px-5 rounded-[10px]">
                                    {genre.name}
                                  </p>
                                </Link>
                              );
                            })
                          : ""}
                      </div>
                    </div>
                  </div>
                  {/* ---------------------------------------------------------------------------- */}
                  <div className="hidden max-md:flex justify-evenly mt-5 mb-5 -ml-2 -mr-3 krub-regular text-[16px] md:text-[18px] flex-nowrap gap-1.5 items-center lg:ml-0 lg:justify-start">
                    <div className="flex items-center gap-1 text-[16px]">
                      <Star size={16} color="yellow" />
                      {tvDetails?.vote_average
                        ? tvDetails.vote_average.toFixed(2)
                        : null}

                      {/* <img
                        src="/star.png"
                        alt="star"
                        className="w-[14px] md:w-[20px] h-full -mt-[3px]"
                      /> */}
                    </div>
                    <div className="relative flex items-center justify-center mx-1">
                      <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                      <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                    </div>
                    <h2>
                      {tvDetails?.first_air_date
                        ? tvDetails.first_air_date.substring(0, 4)
                        : null}
                    </h2>
                    <div className="relative flex items-center justify-center mx-1">
                      <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                      <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                    </div>
                    <div className="text-xl flex items-center gap-2 text-[grey]">
                      [{" "}
                      <span className="text-[12px] text-white">
                        {tvCert}
                      </span>{" "}
                      ]
                    </div>
                    <div className="relative flex items-center justify-center mx-1">
                      <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                      <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                    </div>
                    <h2>
                      {tvDetails?.number_of_seasons
                        ? tvDetails.number_of_seasons +
                          (tvDetails.number_of_seasons === 1
                            ? " Season"
                            : " Seasons")
                        : null}
                    </h2>
                    <div className="relative flex items-center justify-center mx-1">
                      <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                      <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                    </div>
                    <h2>
                      {runtimeMinutes > 0 ? runtime : null}
                    </h2>
                  </div>

                  <div className="hidden max-md:flex flex-wrap justify-center gap-2 krub-regular max-sm:text-[12px]/2.5 md:text-[13px] mt-3 ml-3 lg:ml-0">
                    {tvDetails?.genres
                      ? tvDetails.genres.map((genre) => {
                          return (
                            <Link key={genre.id}>
                              <p className="bg-[#1b2028] border-[2px] border-[#4f75c157] py-2 px-3 mt-1 md:px-4 lg:px-5 rounded-[10px]">
                                {genre.name}
                              </p>
                            </Link>
                          );
                        })
                      : ""}
                  </div>
                  {/* ----------------------------------------------------------------------------------------------- */}
                  <div className="flex flex-col lg:flex-row gap-10 -ml-2 -mr-2 mt-5">
                    <div className="flex-6">
                      <div className="border rounded-lg border-[#2b417a69] bg-[#0a0f22d1] px-5 py-3 h-fit">
                        <h2 className="flex items-center gap-2 text-xl lg:text-2xl font-bold mb-4">
                          <Summary color="magenta" />
                          Synopsis
                        </h2>
                        <h2 className="text-[#ffffff7c] text-[17px] lg:text-[18px] font-normal mb-4 md:mt-5">
                          {tvDetails?.overview}
                        </h2>
                      </div>
                      <div className="flex items-center justify-between mb-5 mt-10 lg:mt-20 ">
                        <h2 className="text-xl font-bold ">Cast</h2>
                        <Link to={`/tv/${id}/cast`}>
                          <h3 className="text-[#3668e8] text-[18px] md:font-bold">
                            See All
                          </h3>
                        </Link>
                      </div>

                      <div className="mb-20 scrollhide">
                        <div className="grid grid-cols-3 md:grid-cols-6  gap-4">
                          {cast
                            ? cast.slice(0, 6).map((casting) => (
                                <div
                                  key={casting.id}
                                  className=" hover:scale-[1.1] duration-100"
                                >
                                  <CastCard
                                    key={casting.id}
                                    image={
                                      casting.profile_path === null
                                        ? `/no-poster.jpg`
                                        : "https://image.tmdb.org/t/p/w185" + casting.profile_path
                                    }
                                    name={casting.name}
                                    char={casting.character}
                                  />
                                </div>
                              ))
                            : ""}
                        </div>
                      </div>
                    </div>
                    <div className="flex-2 hidden lg:flex flex-col items-start gap-10 sticky top-5 self-start">
                      <div className="img-skeleton w-50 h-[300px] md:w-60 md:h-[360px] rounded-2xl">
                        <img
                          src={IMGPATH + tvDetails?.poster_path}
                          alt={tvDetails?.name}
                          loading="eager"
                          decoding="async"
                          className="w-full h-full object-cover rounded-2xl border-[2px] border-[#3fb2d5] shadow-[0px_0px_7px_0px_rgba(63,178,213,1)] img-fade-in"
                          onLoad={(e) => e.target.classList.add("loaded")}
                          onError={(e) => { e.target.src = "/placeholder.png"; }}
                        />
                      </div>
                      <div className="border rounded-lg border-[#2b417a69] bg-[#0a0f22d1] px-5 py-3 h-fit">
                        <h2 className="text-xl lg:text-2xl font-bold mb-4">
                          Synopsis
                        </h2>
                        <h2 className="text-[#ffffff7c] text-[17px] lg:text-[18px] font-normal mb-4 md:mt-5">
                          {tvDetails?.overview}
                        </h2>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <SourceSelectModal
          open={sourceOpen}
          onClose={() => setSourceOpen(false)}
          onSelect={(source) => {
            setSourceOpen(false);
            const watchPath = `/tv/${tvDetails?.id || id}/watch`;
            const params = new URLSearchParams({
              season: String(seasonNumber),
              episode: String(episodeNumber),
            });
            if (source === "vidnest") params.set("source", "2");
            else if (source === "netnaija") params.set("source", "3");
            navigate(`${watchPath}?${params.toString()}`);
          }}
        />
      </>
    );
  }

  // No id: keep the original minimal browse behaviour
  if (!id) {
    return (
      <div className="text-white flex flex-col w-full ml-[4%] mr-[4%]">
        <SearchBar />
      </div>
    );
  }

  return (
    <div className="text-white flex justify-center mt-10 ">
      <Tv_details />
    </div>
  );
}

export default TVShows;
