// import { useState } from "react";

const API_KEY = import.meta.env.VITE_TMDB_API_KEY;
const BASE_URL = import.meta.env.VITE_TMDB_BASE_URL;

// Helper function to handle the actual fetch logic
const fetchFromTMDB = async (endpoint, params = "") => {
  try {
    const response = await fetch(
      `${BASE_URL}${endpoint}?api_key=${API_KEY}${params}`,
    );
    if (!response.ok) throw new Error("Network response was not ok");
    return await response.json();
  } catch (error) {
    console.error("API Fetch Error:", error);
    return null;
  }
};

// endpoint here would be either movie or tv
const searchFromTMDB = async (endpoint, searchValue = "", params = "") => {
  try {
    const response = await fetch(
      `${BASE_URL}/search/${endpoint}?query=${encodeURIComponent(searchValue)}&api_key=${API_KEY}${params}`,
    );
    if (!response.ok) throw new Error("Network response was not ok");
    return await response.json();
  } catch (error) {
    console.error("API Fetch Error:", error);
    return null;
  }
};

// Normalise titles so punctuation/case do not stop an exact match from winning
// ("Mrs.", "Mr. & Mrs. Smith", "Spider-Man", …).
const normalizeTitle = (value) =>
  String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();

const getResultTitle = (result) =>
  result.title || result.name || result.original_title || result.original_name || "";

const rankSearchResults = (results, query) => {
  const normalizedQuery = normalizeTitle(query);
  if (!normalizedQuery) return results;

  const scoreOf = (result) => {
    const title = normalizeTitle(getResultTitle(result));
    if (!title) return 4;
    if (title === normalizedQuery) return 0;
    if (title.startsWith(`${normalizedQuery} `)) return 1;
    if (title.startsWith(normalizedQuery)) return 2;
    if (title.includes(normalizedQuery)) return 3;
    return 4;
  };

  return results
    .map((result, index) => ({ result, index, score: scoreOf(result) }))
    .sort((a, b) => a.score - b.score || a.index - b.index)
    .map((entry) => entry.result);
};

// 1. Calculate the date window (45 days)
const today = new Date();
const startDate = new Date();
const dateYear = today.getFullYear().toString();
const dateMonth = (today.getMonth() + 1).toString().padStart(2, "0");
const todayDate = today.getDate().toString().padStart(2, "0");
const todayFullDate = dateYear + "-" + dateMonth + "-" + todayDate;
startDate.setDate(today.getDate() - 45);
const formatDate = (date) => date.toISOString().split("T")[0];

// hi: Hindi, en: English, pa: Punjabi, te: Telugu, ta: Tamil, ml: Malayalam, kn: Kannada
const languages = ["hi", "en", "pa", "te", "ta", "ml", "kn"].join("|");
const langHindi = "hi";
const langEng = "en";
const langPunjabi = "pa";
const releaseTypes = "2|3|4";

export const movieAPI = {
  getHindiMovies: (page = 1) =>
    fetchFromTMDB(
      "/discover/movie",
      `&page=${page}&region=IN&with_release_type=${releaseTypes}&with_original_language=${langHindi}&release_date.gte=${formatDate(
        startDate,
      )}&release_date.lte=${formatDate(today)}&sort_by=popularity.desc`,
    ),

  getEnglishMovies: (page = 1) =>
    fetchFromTMDB(
      "/discover/movie",
      `&page=${page}&region=IN&with_release_type=${releaseTypes}&with_original_language=${langEng}&release_date.gte=${formatDate(
        startDate,
      )}&release_date.lte=${formatDate(today)}&sort_by=popularity.desc`,
    ),

  getPunjabiMovies: (page = 1) =>
    fetchFromTMDB(
      "/discover/movie",
      `&page=${page}&region=IN&with_release_type=${releaseTypes}&with_original_language=${langPunjabi}&release_date.lte=${formatDate(today)}&sort_by=release_date.desc`,
    ),

  getFeaturedMovies: () =>
    fetchFromTMDB(
      "/discover/movie",
      `&region=IN&with_release_type=${releaseTypes}&with_original_language=${languages}&release_date.gte=${formatDate(
        startDate,
      )}&release_date.lte=${formatDate(today)}&sort_by=popularity.desc`,
    ),

  getAllMovies: (page = 1, gen = "", year = "") =>
    fetchFromTMDB(
      "/discover/movie",
      `&region=IN&&sort_by=popularity.desc&primary_release_date.lte=${todayFullDate}&with_original_language=hi|pa|te|ta|ml|kn&primary_release_year=${year}&page=${page}&with_genres=${gen}`,
    ),

  getAllHindiMovies: (page = 1, gen = "", year = "") =>
    fetchFromTMDB(
      "/discover/movie",
      `&region=IN&sort_by=popularity.desc&primary_release_date.lte=${todayFullDate}&with_original_language=hi&primary_release_year=${year}&page=${page}&with_genres=${gen}`,
    ),

  getAllEnglishMovies: (page = 1, gen = "", year = "") =>
    fetchFromTMDB(
      "/discover/movie",
      `&region=IN&&sort_by=popularity.desc&primary_release_date.lte=${todayFullDate}&with_original_language=en&primary_release_year=${year}&page=${page}&with_genres=${gen}`,
    ),

  getNowPlaying: (page = 1) =>
    fetchFromTMDB(
      "/discover/movie",
      `&region=IN&sort_by=popularity.desc&with_release_type=3&with_original_language=${langHindi}&release_date.gte=${formatDate(
        startDate,
      )}&release_date.lte=${formatDate(today)}&language=en-US`,
    ),

  getMovieDetails: (id = "") => fetchFromTMDB(`/movie/${id}`, ``),

  getCast: (id = "") => fetchFromTMDB(`/movie/${id}/credits`, ``),

  getCert: (id = "") => fetchFromTMDB(`/movie/${id}/release_dates`, ``),

  getGenres: () => fetchFromTMDB(`/genre/movie/list`, `&language=en`),

  searchMovies: async (query = "") => {
    const searchValue = String(query || "").trim();
    if (!searchValue) return { results: [] };

    const params = "&include_adult=false&language=en-US&page=1";

    // /search/multi alone often ranks the exact title below many partial
    // matches (e.g. "Mrs." is on page 2). Fetch the type-specific searches
    // too, merge/dedupe, then put the closest title matches first.
    const [multiData, movieData, tvData] = await Promise.all([
      searchFromTMDB("multi", searchValue, params),
      searchFromTMDB("movie", searchValue, params),
      searchFromTMDB("tv", searchValue, params),
    ]);

    const movieResults = (movieData?.results || []).map((result) => ({
      ...result,
      media_type: "movie",
    }));
    const tvResults = (tvData?.results || []).map((result) => ({
      ...result,
      media_type: "tv",
    }));

    const seen = new Set();
    const merged = [];
    for (const result of [
      ...(multiData?.results || []),
      ...movieResults,
      ...tvResults,
    ]) {
      if (!result || result.id == null) continue;
      const key = `${result.media_type || "unknown"}:${result.id}`;
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push(result);
    }

    return { results: rankSearchResults(merged, searchValue) };
  },
};

export const tv_shows = `https://api.themoviedb.org/3/discover/tv?include_adult=false&api_key=04c35731a5ee918f014970082a0088b1&include_null_first_air_dates=false&&page=1&sort_by=popularity.desc&region=IN&with_original_language=hi`;

export const tv_details_1 = `https://api.themoviedb.org/3/tv/`;

export const tv_details_2 = `?api_key=04c35731a5ee918f014970082a0088b1`;

export const castTv_api_1 = `https://api.themoviedb.org/3/tv/`;
export const castTv_api_2 = `/credits?api_key=04c35731a5ee918f014970082a0088b1&language=en-US`;

export const movie_trailer_1 = `https://api.themoviedb.org/3/movie/`;
export const movie_trailer_2 = `/videos?language=en-US&api_key=04c35731a5ee918f014970082a0088b1`;

// https://api.themoviedb.org/3/movie/37822/videos?language=en-US&api_key=04c35731a5ee918f014970082a0088b1

// const Next_btn = () => {
//   const [prevpage, setprevpage] = useState(1);
//   const [newPage, setNewPage] = useState(prevpage);
//   return (
//     <div className="text-[black]  flex justify-center">
//       <button
//         className="bg-white cursor-pointer mt-10 p-2"
//         onClick={(e) => {
//           setNewPage(newPage + 1);
//           setprevpage(newPage);
//         }}
//       >
//         Page {newPage}
//       </button>
//     </div>
//   );
// };

// export { Next_btn };
