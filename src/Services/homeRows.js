import { movieAPI } from "./api";

/**
 * Single source of truth for the home rows.
 *
 * Each spec is also used by /collection/:id, so a "More" link always has a
 * real destination that can page through the same query.
 */
export const homeRowSpecs = [
  {
    id: "top-10",
    title: "Top 10 Today",
    eyebrow: "The whole country is watching",
    description:
      "The ten Indian films everyone is talking about right now, ranked by today's buzz.",
    mediaType: "movie",
    variant: "ranked",
    accent: "from-amber-300 via-yellow-400 to-orange-600",
    limit: 10,
    more: "/collection/top-10",
    fetchPage: (page) => movieAPI.getTop10Today(page),
  },
  {
    id: "now-playing",
    title: "Now Playing",
    eyebrow: "Hindi first · then English · Punjabi · pan-India",
    description:
      "Fresh theatrical releases, led by Hindi cinema, then English, Punjabi and the rest of India — tuned for Delhi audiences.",
    collectionEyebrow: "All languages · last 90 days",
    collectionDescription:
      "Every theatrical release from the last 90 days, sorted by popularity across Hindi, English, Punjabi and the rest of India.",
    mediaType: "movie",
    variant: "poster",
    accent: "from-cyan-300 to-blue-600",
    badge: "In cinemas",
    limit: 18,
    more: "/collection/now-playing",
    fetchRow: () => movieAPI.getNowPlayingRow(),
    fetchPage: (page) => movieAPI.getNowPlayingMovies(page),
  },
  {
    id: "blockbuster-hindi",
    title: "Blockbuster Hindi Movies",
    eyebrow: "Bollywood's biggest nights",
    description:
      "The Hindi releases drawing the biggest crowds, from mass entertainers to prestige dramas.",
    mediaType: "movie",
    variant: "poster",
    accent: "from-rose-400 to-red-700",
    limit: 14,
    more: "/movies/hindi",
    fetchPage: (page) => movieAPI.getHindiMovies(page),
  },
  {
    id: "english-picks",
    title: "English Movies to Watch",
    eyebrow: "Handpicked Hollywood and beyond",
    description:
      "English-language films in Indian theatres and on the radar, sorted by what people are watching.",
    mediaType: "movie",
    variant: "poster",
    accent: "from-sky-300 to-indigo-600",
    limit: 14,
    more: "/movies/english",
    fetchPage: (page) => movieAPI.getEnglishMovies(page),
  },
  {
    id: "south-indian",
    title: "South Indian Hits",
    eyebrow: "Telugu · Tamil · Malayalam · Kannada",
    description:
      "The biggest crowd-pullers from the four South Indian film industries.",
    mediaType: "movie",
    variant: "wide",
    accent: "from-emerald-300 to-teal-600",
    limit: 12,
    more: "/collection/south-indian",
    fetchPage: (page) => movieAPI.getSouthIndianMovies(page),
  },
  {
    id: "punjabi",
    title: "Latest Punjabi Movies",
    eyebrow: "Pollywood, freshly arrived",
    description:
      "New Punjabi releases, straight from the last few months of Pollywood.",
    mediaType: "movie",
    variant: "poster",
    accent: "from-fuchsia-400 to-purple-700",
    limit: 14,
    more: "/collection/punjabi",
    fetchPage: (page) => movieAPI.getLatestPunjabiMovies(page),
  },
  {
    id: "critically-acclaimed",
    title: "Critically Acclaimed",
    eyebrow: "The critics' shortlist",
    description:
      "High-scoring films with a strong audience and critic footprint — the ones worth clearing an evening for.",
    mediaType: "movie",
    variant: "wide",
    accent: "from-violet-300 to-indigo-700",
    badge: "Acclaimed",
    emphasis: "score",
    limit: 12,
    more: "/collection/critically-acclaimed",
    fetchPage: (page) => movieAPI.getCriticallyAcclaimedMovies(page),
  },
  {
    id: "trending-tv",
    title: "Trending TV Shows",
    eyebrow: "Binge-worthy series right now",
    description:
      "The series climbing the global watch charts this week.",
    mediaType: "tv",
    variant: "poster",
    accent: "from-orange-300 to-rose-600",
    limit: 14,
    more: "/collection/trending-tv",
    fetchPage: (page) => movieAPI.getTrendingTvShows(page),
  },
  {
    id: "top-rated-series",
    title: "Top Rated Series",
    eyebrow: "The best of television",
    description:
      "The highest-rated series of all time, filtered so only shows with a real audience make the cut.",
    mediaType: "tv",
    variant: "wide",
    accent: "from-lime-300 to-emerald-600",
    emphasis: "score",
    limit: 12,
    more: "/collection/top-rated-series",
    fetchPage: (page) => movieAPI.getTopRatedTvShows(page),
  },
];

export const findHomeRow = (id) =>
  homeRowSpecs.find((spec) => spec.id === id) || null;

/**
 * Keeps only usable TMDB results, removes duplicates and caps the list.
 * Used by both the home row (page 1) and the collection page.
 */
export const prepareResults = (spec, results, limit = spec?.limit ?? 14) => {
  const seen = new Set();
  return (Array.isArray(results) ? results : [])
    .filter((item) => {
      if (!item || item.id == null || item.adult) return false;
      if (!item.title && !item.name) return false;
      if (spec?.variant === "wide") {
        return Boolean(item.backdrop_path || item.poster_path);
      }
      return Boolean(item.poster_path);
    })
    .filter((item) => {
      if (seen.has(item.id)) return false;
      seen.add(item.id);
      return true;
    })
    .slice(0, limit);
};
