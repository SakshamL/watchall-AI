import React, { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import SearchBar from "../Components/SearchBar";
import HomePosterCard from "../Components/HomePosterCard";
import HomeWideCard from "../Components/HomeWideCard";
import { findHomeRow, prepareResults } from "../Services/homeRows";

function CollectionPage() {
  const { slug } = useParams();
  const spec = findHomeRow(slug);
  const [page, setPage] = useState(1);
  const [items, setItems] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const singlePage = spec?.variant === "ranked";

  useEffect(() => {
    setPage(1);
  }, [slug]);

  useEffect(() => {
    if (!spec) {
      setLoading(false);
      return undefined;
    }

    let alive = true;
    setLoading(true);

    spec
      .fetchPage(page)
      .then((data) => {
        if (!alive) return;
        setItems(
          singlePage
            ? prepareResults(spec, data?.results, spec.limit ?? 10)
            : prepareResults(spec, data?.results, 20),
        );
        const pages = Number(data?.total_pages);
        setTotalPages(
          singlePage
            ? 1
            : Number.isFinite(pages) && pages > 0
              ? Math.min(pages, 500)
              : 1,
        );
      })
      .catch(() => {
        if (!alive) return;
        setItems([]);
        setTotalPages(1);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, [page, singlePage, spec]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [page, slug]);

  if (!spec) {
    return (
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-4 text-white">
        <h1 className="text-2xl font-bold">Collection not found</h1>
        <p className="text-white/50">
          That shelf does not exist. Head back to the homepage to keep browsing.
        </p>
        <Link
          to="/"
          className="rounded-full bg-gradient-to-r from-indigo-700 to-cyan-500 px-5 py-2 font-semibold text-white shadow-lg"
        >
          Back to Home
        </Link>
      </div>
    );
  }

  const wide = spec.variant === "wide";
  const skeletonCount = wide ? 6 : 14;

  return (
    <div className="w-full text-white">
      <title>{`${spec.title} - WatchAll`}</title>

      <SearchBar />

      <div className="mt-10 flex items-center gap-3">
        <span
          className={`h-10 w-[3px] shrink-0 rounded-full bg-gradient-to-b ${spec.accent}`}
          aria-hidden="true"
        />
        <div>
          <p className="text-[9px] font-semibold uppercase tracking-[0.22em] text-white/40">
            {spec.collectionEyebrow || spec.eyebrow}
          </p>
          <h1 className="mt-0.5 text-2xl font-bold md:text-3xl">{spec.title}</h1>
        </div>
      </div>
      {spec.collectionDescription || spec.description ? (
        <p className="mt-3 max-w-3xl text-sm leading-relaxed text-white/50 md:text-[15px]">
          {spec.collectionDescription || spec.description}
        </p>
      ) : null}

      <div
        className={`mt-8 grid gap-x-4 gap-y-7 ${
          wide
            ? "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3"
            : "grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-7"
        }`}
      >
        {loading
          ? Array.from({ length: skeletonCount }).map((_, index) => (
              <div
                key={`skeleton-${index}`}
                className={`img-skeleton rounded-[12px] ${
                  wide ? "aspect-video w-full" : "aspect-[2/3] w-full"
                }`}
              />
            ))
          : items.map((item, index) => (
              <div key={item.id} className="w-full">
                {wide ? (
                  <HomeWideCard
                    item={item}
                    mediaType={spec.mediaType}
                    badge={spec.badge}
                    emphasis={spec.emphasis}
                  />
                ) : (
                  <HomePosterCard
                    item={item}
                    mediaType={spec.mediaType}
                    rankBadge={
                      spec.variant === "ranked" ? (page - 1) * 20 + index + 1 : 0
                    }
                  />
                )}
              </div>
            ))}
      </div>

      {!loading && items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-20 text-white/50">
          <p>No titles found for this shelf right now.</p>
          <Link to="/" className="text-cyan-300 hover:text-white">
            Back to Home
          </Link>
        </div>
      ) : null}

      {!loading && !singlePage && items.length > 0 ? (
        <div className="mb-20 mt-10 flex items-center justify-center gap-3 text-sm text-white/60">
          <button
            type="button"
            onClick={() => setPage((value) => Math.max(1, value - 1))}
            disabled={page <= 1}
            className={`rounded-full border px-5 py-2 transition ${
              page <= 1
                ? "cursor-not-allowed border-white/10 text-white/25"
                : "cursor-pointer border-white/25 hover:border-cyan-300/60 hover:text-white"
            }`}
          >
            Previous
          </button>
          <span className="min-w-[110px] text-center">
            Page {page} of {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
            disabled={page >= totalPages}
            className={`rounded-full border px-5 py-2 transition ${
              page >= totalPages
                ? "cursor-not-allowed border-white/10 text-white/25"
                : "cursor-pointer border-white/25 hover:border-cyan-300/60 hover:text-white"
            }`}
          >
            Next
          </button>
        </div>
      ) : null}
    </div>
  );
}

export default CollectionPage;
