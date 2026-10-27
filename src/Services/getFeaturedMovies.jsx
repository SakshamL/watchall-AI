import React from "react";
import { useState, useEffect } from "react";
import FeaturedMoviesCards from "../Components/FeaturedMoviesCards";
// import { top_rated_movies_1, top_rated_movies_2 } from "./api";
import { movieAPI } from "./api";

const BACKDROP_PATH = "https://image.tmdb.org/t/p/w780";  // for featured backdrop cards
const POSTER_PATH = "https://image.tmdb.org/t/p/w342";    // for poster thumbnails

const bg_path_1 = "https://api.themoviedb.org/3/movie/";
const bg_path_2 =
  "/images?api_key=04c35731a5ee918f014970082a0088b1&language=en-US&include_image_language=en";

//   https://api.themoviedb.org/3/movie/83533/images?api_key=04c35731a5ee918f014970082a0088b1&language=en-US&include_image_language=en

const GetFeaturedMovies = () => {
  const page_no = 1;
  const [topMovies, setTopMovies] = useState([]);

  useEffect(() => {
    getTopMovies();
  }, []);

  // useEffect(() => {
  //   getTopMovies();
  // }, []);

  const getTopMovies = async () => {
    const cachedData = sessionStorage.getItem("featured_movies");
    if (cachedData) {
      // Logic: If cache exists, use it
      setTopMovies(JSON.parse(cachedData));
    } else {
      const response = await movieAPI.getFeaturedMovies();
      // 1. Get the first 3 movies from the initial list
      const firstThree = response.results.slice(0, 4);

      // 2. Map through those 3 and fetch their specific backdrop images
      const enrichedMovies = await Promise.all(
        firstThree.map(async (movie) => {
          const imageRes = await fetch(bg_path_1 + movie.id + bg_path_2);
          const imageData = await imageRes.json();

          // 3. Pick the first backdrop from the results, or fallback to the default if empty
          const customBackdrop =
            imageData.backdrops && imageData.backdrops[0]
              ? imageData.backdrops[0].file_path
              : movie.backdrop_path;

          return {
            ...movie,
            custom_backdrop: customBackdrop,
          };
        }),
      );
      sessionStorage.setItem("featured_movies", JSON.stringify(enrichedMovies));
      setTopMovies(enrichedMovies);
    }

    // setTopMovies(responseJSON.results);
  };

  return (
    <div className="grid grid-cols-2 gap-4 gap-y-0 md:grid-cols-4 duration-200 ease-in-out">
      {topMovies.map((movie) => {
        return (
          <div key={movie.id} className="w-full h-[80%] relative">
            <FeaturedMoviesCards
              key={movie.id}
              id={movie.id}
              title={movie.title}
              backdrop={BACKDROP_PATH + movie.custom_backdrop}
              poster={POSTER_PATH + movie.poster_path}
              vote={movie.vote_average}
            />
            ;
          </div>
        );
      })}
    </div>
  );
};

export default GetFeaturedMovies;

// const backdrop_image = await fetch(bg_path_1 + movie.id + bg_path_2);
//         const responseJSON2 = await backdrop_image.json();
//         console.log(responseJSON2.backdrops[1].file_path);
