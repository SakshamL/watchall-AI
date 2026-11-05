const LANGUAGE_LABELS = {
  hi: "Hindi",
  en: "English",
  pa: "Punjabi",
  te: "Telugu",
  ta: "Tamil",
  ml: "Malayalam",
  kn: "Kannada",
  mr: "Marathi",
  bn: "Bengali",
  gu: "Gujarati",
  ur: "Urdu",
  ja: "Japanese",
  ko: "Korean",
  fr: "French",
  es: "Spanish",
  de: "German",
  it: "Italian",
  pt: "Portuguese",
  ru: "Russian",
  zh: "Chinese",
  ar: "Arabic",
  tr: "Turkish",
  th: "Thai",
  id: "Indonesian",
  nl: "Dutch",
  sv: "Swedish",
  da: "Danish",
  no: "Norwegian",
  pl: "Polish",
  he: "Hebrew",
  fa: "Persian",
};

export const titleOf = (item) =>
  item?.title ||
  item?.name ||
  item?.original_title ||
  item?.original_name ||
  "Untitled";

export const slugifyTitle = (title) => {
  const slug = String(title)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "watch";
};

export const linkFor = (item, mediaType = "movie") =>
  mediaType === "tv"
    ? `/tv/${item.id}`
    : `/movie/${item.id}/${slugifyTitle(titleOf(item))}`;

export const languageLabel = (code) =>
  LANGUAGE_LABELS[String(code || "").toLowerCase()] || "";

export const ratingLabel = (value) => {
  const rating = Number(value);
  if (!Number.isFinite(rating) || rating <= 0) return "";
  return rating.toFixed(1);
};
