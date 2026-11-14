// Small helpers shared by the movie and TV details pages.

export const formatRuntime = (minutes) => {
  const total = Number(minutes);
  if (!Number.isFinite(total) || total <= 0) return "";
  const hours = Math.floor(total / 60);
  const mins = Math.round(total % 60);
  if (!hours) return `${mins}m`;
  return mins ? `${hours}h ${mins}m` : `${hours}h`;
};

// TMDB returns one entry per country. Indian certification first, then the US,
// then whatever has a rating so the badge is never empty when data exists.
export const pickMovieCertification = (results) => {
  if (!Array.isArray(results)) return "";
  const certOf = (entry) =>
    entry?.release_dates?.find((date) => date?.certification)?.certification ||
    "";
  const indian = results.find((entry) => entry?.iso_3166_1 === "IN");
  const american = results.find((entry) => entry?.iso_3166_1 === "US");
  return (
    certOf(indian) || certOf(american) || results.map(certOf).find(Boolean) || ""
  );
};

export const pickTvCertification = (results) => {
  if (!Array.isArray(results)) return "";
  const ratingOf = (entry) => entry?.rating || "";
  const indian = results.find((entry) => entry?.iso_3166_1 === "IN");
  const american = results.find((entry) => entry?.iso_3166_1 === "US");
  return (
    ratingOf(indian) ||
    ratingOf(american) ||
    results.map(ratingOf).find(Boolean) ||
    ""
  );
};

export const pickTrailer = (results) => {
  if (!Array.isArray(results) || !results.length) return "";
  const youtube = results.filter(
    (video) => video?.site === "YouTube" && video?.key,
  );
  const official = youtube.find(
    (video) => video.type === "Trailer" && video.official,
  );
  const trailer = youtube.find((video) => video.type === "Trailer");
  return (official || trailer || youtube[0])?.key || "";
};


