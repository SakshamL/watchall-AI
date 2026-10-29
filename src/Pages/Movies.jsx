import React from "react";
import { useState, useEffect, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { movieAPI } from "../Services/api";
import { movie_trailer_1, movie_trailer_2 } from "../Services/api";
import SearchBar from "../Components/SearchBar";
import { Link } from "react-router-dom";
import CastCard from "../Components/CastCard";
import AllMoviesPage from "./AllMoviesPage";
import SourceSelectModal from "../Components/SourceSelectModal";
import { Play } from "lucide-react";
import { Film } from "lucide-react";
import { Plus } from "lucide-react";
import { Star } from "lucide-react";
import { Circle } from "lucide-react";
import { Summary } from "lucide-react";

const IMGPATH = "https://image.tmdb.org/t/p/w780"; // detail page shows large images, w780 is sharp enough

function Movies(props) {
  const { id } = useParams();
  const { title } = useParams();
  const navigate = useNavigate();
  const [movieDetails, setMovieDetails] = useState([]);
  const [cast, setCast] = useState([]);
  const [movieCert, setMovieCert] = useState("");
  const [trailer, setTrailer] = useState([]);
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
    getMovie_details();
    getcast_details();
    get_cert();
    get_trailer();
  }, []);

  useEffect(() => {
    getMovie_details();
    getcast_details();
    get_cert();
    get_trailer();
  }, [id]);

  const getMovie_details = async () => {
    if (id) {
      const response = await movieAPI.getMovieDetails(id);
      // const response = await fetch(movie_details_1 + id + movie_details_2);
      // const responseJSON = await response.json();
      setMovieDetails(response);
    }
  };

  const getcast_details = async () => {
    if (id) {
      const response = await movieAPI.getCast(id);
      // const response = await fetch(cast_api_1 + id + cast_api_2);
      // const responseJSON = await response.json();
      setCast(response.cast);
      // console.log(responseJSON.cast);
    }
  };

  const get_cert = async () => {
    if (id) {
      const response = await movieAPI.getCert(id);
      // const response = await fetch(movie_cert_1 + id + movie_cert_2);
      // const responseJSON = await response.json();
      response.results.forEach((element) => {
        if (element.iso_3166_1 == "IN") {
          setMovieCert(element.release_dates[0].certification);
          // console.log(element.release_dates[0].certification);
        }
      });
    }
  };

  const get_trailer = async () => {
    if (id) {
      const response = await fetch(movie_trailer_1 + id + movie_trailer_2);
      const responseJSON = await response.json();
      setTrailer(responseJSON.results[0].key);
      // console.log(responseJSON.results[0].key);
      // console.log(movie_trailer_1 + id + movie_trailer_2);
    }
  };

  function Movie_details() {
    return (
      <>
        <title>{movieDetails.title + " - WatchAll"}</title>
        <div className="text-white flex flex-col w-full">
          {/* <SearchBar /> */}
          <div className="relative -mt-6">
            <div className="relative w-screen overflow-hidden left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] h-[56vw] max-h-[500px] rounded-none backdrop-mask-gradient img-skeleton">
              <img
                src={IMGPATH + movieDetails.backdrop_path}
                alt=""
                loading="eager"
                fetchpriority="high"
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
                          src={IMGPATH + movieDetails.poster_path}
                          alt={movieDetails.title}
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
                        {movieDetails.title}
                      </h2>
                      <h3 className="hidden md:block italic text-lg text-[#d8d8d8] font-semibold -mt-2">
                        {movieDetails.tagline}
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
                        <div
                          className="flex gap-3 items-center md:col-span-2 lg:col-span-1 lg:w-fit bg-[#0a0f22d1] rounded-[10px] cursor-pointer px-5 py-2 md:py-3 shadow-[0px_0px_3px_0px_rgba(255,_255,_255,_0.3)] font-normal max-lg:text-sm"
                          onClick={() => {}}
                        >
                          <Plus />
                          Watchlist
                        </div>
                      </div>

                      {/* ----------------------------------------------- */}
                      <div className="max-md:hidden lg:w-1/3 flex justify-evenly mt-5 mb-5 -ml-2 -mr-3 krub-regular text-[16px] md:text-[18px] flex-nowrap gap-1.5 items-center lg:ml-0 lg:justify-evenly">
                        <div className="flex items-center gap-1 text-[16px]">
                          <Star size={16} color="yellow" />
                          {movieDetails.vote_average
                            ? movieDetails.vote_average.toFixed(2)
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
                          {movieDetails.release_date
                            ? movieDetails.release_date.substring(0, 4)
                            : null}
                        </h2>
                        <div className="relative flex items-center justify-center mx-1">
                          <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                          <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                        </div>
                        <div className="text-xl flex items-center gap-2 text-[grey]">
                          [{" "}
                          <span className="text-[12px] text-white">
                            {movieCert}
                          </span>{" "}
                          ]
                        </div>
                        <div className="relative flex items-center justify-center mx-1">
                          <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                          <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                        </div>
                        <h2>
                          {Math.trunc(movieDetails.runtime / 60).toString() +
                            "h " +
                            ((movieDetails.runtime / 60) % 1)
                              .toString()
                              .substring(2, 3) *
                              6 +
                            "m" +
                            " "}
                        </h2>
                      </div>
                      <div className="hidden md:flex flex-wrap justify-center lg:justify-start gap-2 krub-regular max-sm:text-[12px]/2.5 md:text-[13px] mt-3 ml-3 lg:ml-0">
                        {movieDetails.genres
                          ? movieDetails.genres.map((genre) => {
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
                      {movieDetails.vote_average
                        ? movieDetails.vote_average.toFixed(2)
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
                      {movieDetails.release_date
                        ? movieDetails.release_date.substring(0, 4)
                        : null}
                    </h2>
                    <div className="relative flex items-center justify-center mx-1">
                      <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                      <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                    </div>
                    <div className="text-xl flex items-center gap-2 text-[grey]">
                      [{" "}
                      <span className="text-[12px] text-white">
                        {movieCert}
                      </span>{" "}
                      ]
                    </div>
                    <div className="relative flex items-center justify-center mx-1">
                      <Circle className="absolute h-2.5 w-2.5 fill-yellow-600 text-yellow-600 opacity-70 blur-[2px]" />
                      <Circle className="relative h-2 w-2 fill-yellow-100 text-yellow-100 opacity-90" />
                    </div>
                    <h2>
                      {Math.trunc(movieDetails.runtime / 60).toString() +
                        "h " +
                        ((movieDetails.runtime / 60) % 1)
                          .toString()
                          .substring(2, 3) *
                          6 +
                        "m" +
                        " "}
                    </h2>
                  </div>

                  <div className="hidden max-md:flex flex-wrap justify-center gap-2 krub-regular max-sm:text-[12px]/2.5 md:text-[13px] mt-3 ml-3 lg:ml-0">
                    {movieDetails.genres
                      ? movieDetails.genres.map((genre) => {
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
                          {movieDetails.overview}
                        </h2>
                      </div>
                      <div className="flex items-center justify-between mb-5 mt-10 lg:mt-20 ">
                        <h2 className="text-xl font-bold ">Cast</h2>
                        <Link to={`/movie/${id}/${title}/cast`}>
                          <h3 className="text-[#3668e8] text-[18px] md:font-bold">
                            See All
                          </h3>
                        </Link>
                      </div>

                      <div className="mb-20 scrollhide">
                        <div className="grid grid-cols-3 md:grid-cols-6  gap-4">
                          {cast.slice(0, 6).map((casting) => (
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
                          ))}
                        </div>
                      </div>
                    </div>
                    <div className="flex-2 hidden lg:flex flex-col items-start gap-10 sticky top-5 self-start">
                      <div className="img-skeleton w-50 h-[300px] md:w-60 md:h-[360px] rounded-2xl">
                        <img
                          src={IMGPATH + movieDetails.poster_path}
                          alt={movieDetails.title}
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
                          {movieDetails.overview}
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
            const watchPath = `/movie/${movieDetails.id || id}/watch`;
            navigate(source === "vidnest" ? `${watchPath}?source=2` : watchPath);
          }}
        />
      </>
    );
  }

  function All_Movies_list(props) {
    return (
      <>
        {/* <h2>Here, You will the list of All Movies..!!</h2> */}

        <AllMoviesPage language={props.language} />
      </>
    );
  }

  // function Show_More_Movies(props) {
  //   return (
  //     <>
  //       {/* <h2>Here, You will the list of All Movies..!!</h2> */}

  //       <ShowMorePage language={props.language} />
  //     </>
  //   );
  // }

  return (
    <div className="text-white flex justify-center mt-10 ">
      {id ? <Movie_details /> : <All_Movies_list language={props.language} />}
    </div>
  );
}

export default Movies;
