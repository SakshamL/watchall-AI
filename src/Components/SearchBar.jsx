import React from "react";
import { useState, useEffect, useRef } from "react";
// import { search_api } from "../Services/api";
import { movieAPI } from "../Services/api";
import SearchCard from "./SearchCard";
import MovieCard from "./MovieCard";
import TVCard from "./TVCard";
import PersonCard from "./PersonCard";

const IMGPATH = "https://image.tmdb.org/t/p/w342"; // w342 is enough for search result thumbnails

function SearchBar() {
  const [searched, setSearched] = useState("");
  const [searchedResults, setSearchResults] = useState([]);
  const requestIdRef = useRef(0);
  // const allowedLanguages = ["hi", "en", "pa", "te", "ta", "ml", "kn"];

  useEffect(() => {
    const timer = setTimeout(getSearchedItems, 500);

    return () => {
      clearTimeout(timer);
    };
  }, [searched]);

  const handleSearchResults = (e) => {
    e.target.value
      ? document.getElementById("searchResults").classList.add("search-results")
      : document
          .getElementById("searchResults")
          .classList.remove("search-results");
    setSearched(e.target.value);
  };

  const getSearchedItems = async () => {
    const requestId = ++requestIdRef.current;
    const response = await movieAPI.searchMovies(searched);
    // Ignore responses from an older query that resolved late.
    if (requestId !== requestIdRef.current) return;
    // const response = await fetch(search_api[0] + searched + search_api[2]);
    // const responseJSON = await response.json();
    // // FILTERING HAPPENS HERE:
    // const filteredResults = responseJSON.results.filter((movie) =>
    //   allowedLanguages.includes(movie.original_language)
    // );
    setSearchResults(response?.results || []);
  };

  return (
    <div className="relative w-full">
      <input
        id="search"
        type="text"
        placeholder="Search Movies and TV Shows"
        className="rounded-[5px] w-full outline-0 border-2 p-3 pl-5 border-[#8080803b] text-[grey]"
        onChange={handleSearchResults}
        value={searched}
      />
      <div
        id="searchResults"
        className="text-2xl text-[grey] flex justify-center items-center searchBar-gradient h-0 invisible "
      >
        {" "}
        {searchedResults.length > 0 ? (
          searchedResults
            .filter((item) => item.media_type != "person")
            .map((result) => {
              return (
                <div key={result.id}>
                  <div className="h-[200px] w-[150px] relative">
                    {/* <SearchCard */}
                    {result.media_type == "movie" ? (
                      <MovieCard
                        key={result.id}
                        id={result.id}
                        title={result.title}
                        poster_path={
                          (result.poster_path === null) |
                          (result.poster_path === undefined)
                            ? `no-poster.jpg`
                            : IMGPATH + result.poster_path
                        }
                        vote={result.vote_average}
                        media_type="movie"
                        searchedCard={true}
                      />
                    ) : result.media_type == "tv" ? (
                      <TVCard
                        key={result.id}
                        id={result.id}
                        name={result.name}
                        poster_path={
                          (result.poster_path === null) |
                          (result.poster_path === undefined)
                            ? `no-poster.jpg`
                            : IMGPATH + result.poster_path
                        }
                        vote={result.vote_average}
                        media_type="tv"
                        searchedCard={true}
                      />
                    ) : (
                      <PersonCard
                        key={result.id}
                        id={result.id}
                        name={result.name}
                        poster_path={
                          (result.profile_path === null) |
                          (result.profile_path === undefined)
                            ? `no-poster.jpg`
                            : IMGPATH + result.profile_path
                        }
                        media_type="Person"
                        searchedCard={true}
                      />
                    )}
                  </div>
                </div>
              );
            })
        ) : (
          <p>No Result Found..!!</p>
        )}
      </div>
    </div>
  );
}

export default SearchBar;
