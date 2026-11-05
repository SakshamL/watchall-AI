import React from "react";
import { useEffect } from "react";
import SearchBar from "../Components/SearchBar";
import HomeRows from "../Sections/HomeRows";
import GetFeaturedMovies from "../Services/getFeaturedMovies";
// import MovieStreamer from "./testplyaer";

function HomePage() {
  useEffect(() => {
    // Clear movie-specific session data whenever Home is visited
    sessionStorage.removeItem("genre");
    sessionStorage.removeItem("year");
    sessionStorage.removeItem("page");
    // sessionStorage.removeItem("genre-hi");
    // console.log("Movie filters cleared for fresh navigation");
    const keys = Object.keys(sessionStorage);
    keys.forEach((key) => {
      if (key.startsWith("genre-")) {
        sessionStorage.removeItem(key);
      }
    });
  }, []);
  return (
    <>
      <title>{"WatchAll Media"}</title>
      <div className=" text-white flex flex-col items-center mt-10">
        <SearchBar />
      </div>

      <div className="mt-10 ">
        <GetFeaturedMovies />

        <HomeRows />
      </div>
      {/* TV Shows */}
    </>
  );
}

export default HomePage;
