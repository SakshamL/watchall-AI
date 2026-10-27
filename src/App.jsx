import { useEffect } from "react";
import "./App.css";
import Navbar from "./Sections/Navbar";
import { Route, Routes, useLocation } from "react-router-dom";
import Movies from "./Pages/Movies";
import HomePage from "./Pages/HomePage";
import TVShows from "./Pages/TVShows";
import Genres from "./Pages/Genres";
import VideoPlayer from "./Pages/VideoPlayer";

function App() {
  const { pathname } = useLocation();
  const isWatchRoute = /^\/movie\/[^/]+\/watch\/?$/.test(pathname);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  useEffect(() => {
    document.documentElement.classList.toggle("wa-watch-active", isWatchRoute);
    document.body.classList.toggle("wa-watch-active", isWatchRoute);
    return () => {
      document.documentElement.classList.remove("wa-watch-active");
      document.body.classList.remove("wa-watch-active");
    };
  }, [isWatchRoute]);

  return (
    <>
      <div>
        {!isWatchRoute ? <Navbar /> : null}

        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/movies" element={<Movies />} />
          <Route path="/movies/hindi" element={<Movies language="hi" />} />
          <Route path="/movies/english" element={<Movies language="en" />} />
          <Route path="/tv" element={<TVShows />} />
          <Route path="/genres" element={<Genres />} />
          <Route path="/movie/:id/:title" element={<Movies />} />
          <Route path="/tv/:id" element={<TVShows />} />
          <Route path="/movie/:id/watch" element={<VideoPlayer />} />
        </Routes>
      </div>
    </>
  );
}

export default App;
