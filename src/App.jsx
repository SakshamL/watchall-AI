import { lazy, Suspense, useEffect } from "react";
import "./App.css";
import Navbar from "./Sections/Navbar";
import { Route, Routes, useLocation } from "react-router-dom";

// One chunk per page. The static imports pulled the player and every listing
// screen into the entry bundle, so the first paint waited on code the visitor
// had not asked for yet.
const HomePage = lazy(() => import("./Pages/HomePage"));
const Movies = lazy(() => import("./Pages/Movies"));
const TVShows = lazy(() => import("./Pages/TVShows"));
const Genres = lazy(() => import("./Pages/Genres"));
const VideoPlayer = lazy(() => import("./Pages/VideoPlayer"));
const CollectionPage = lazy(() => import("./Pages/CollectionPage"));

const RouteFallback = () => (
  <div className="flex min-h-[60vh] w-full items-center justify-center">
    <span className="h-8 w-8 animate-spin rounded-full border-2 border-white/15 border-t-white" />
  </div>
);

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

        <Suspense fallback={<RouteFallback />}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/movies" element={<Movies />} />
            <Route path="/movies/hindi" element={<Movies language="hi" />} />
            <Route path="/movies/english" element={<Movies language="en" />} />
            <Route path="/collection/:slug" element={<CollectionPage />} />
            <Route path="/tv" element={<TVShows />} />
            <Route path="/genres" element={<Genres />} />
            <Route path="/movie/:id/:title" element={<Movies />} />
            <Route path="/tv/:id" element={<TVShows />} />
            <Route path="/movie/:id/watch" element={<VideoPlayer />} />
          </Routes>
        </Suspense>
      </div>
    </>
  );
}

export default App;
