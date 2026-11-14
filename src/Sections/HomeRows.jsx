import React, { useEffect, useMemo, useState } from "react";
import HomeRow from "../Components/HomeRow";
import HomePosterCard from "../Components/HomePosterCard";
import HomeWideCard from "../Components/HomeWideCard";
import { movieAPI } from "../Services/api";
import {
  homeRowSpecs,
  prepareResults,
} from "../Services/homeRows";
import { formatTime, resumeKey } from "../Components/Player/playerUtils";

const cleanResults = (spec, results) => prepareResults(spec, results);

function RowSection({ spec }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);

    const request = spec.fetchRow ? spec.fetchRow() : spec.fetchPage(1);

    request
      .then((data) => {
        if (!alive) return;
        setItems(cleanResults(spec, data?.results));
      })
      .catch(() => {
        if (alive) setItems([]);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, [spec]);

  if (!loading && items.length === 0) return null;

  return (
    <HomeRow
      title={spec.title}
      eyebrow={spec.eyebrow}
      accent={spec.accent}
      moreLink={spec.more}
      loading={loading}
      skeletonCount={spec.variant === "wide" ? 5 : 8}
      skeletonVariant={
        spec.variant === "wide"
          ? "wide"
          : spec.variant === "ranked"
            ? "ranked"
            : "poster"
      }
    >
      {items.map((item, index) => (
        <div
          key={`${spec.id}-${item.id}`}
          className={`flex flex-shrink-0 items-end ${
            spec.variant === "ranked"
              ? "w-[270px]"
              : spec.variant === "wide"
                ? "w-[280px] sm:w-[320px]"
                : "w-[152px] sm:w-[168px] md:w-[180px]"
          }`}
        >
          {spec.variant === "wide" ? (
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
              rank={spec.variant === "ranked" ? index + 1 : 0}
              badge={spec.badge}
            />
          )}
        </div>
      ))}
    </HomeRow>
  );
}

const readResumeEntries = () => {
  try {
    const prefix = resumeKey("");
    return Object.keys(window.localStorage)
      .filter((key) => key.startsWith(prefix))
      .map((key) => {
        const rawId = key.slice(prefix.length);
        const id = rawId.replace(/^movie-/, "");
        // TV resume keys include the season and episode; they are not movie ids.
        if (!/^\d+$/.test(id)) return null;
        try {
          const value = JSON.parse(window.localStorage.getItem(key));
          const position = Number(value?.position);
          const updated = Number(value?.updated);
          if (!id || !Number.isFinite(position) || position <= 5) return null;
          return { id, position, updated: Number.isFinite(updated) ? updated : 0 };
        } catch {
          return null;
        }
      })
      .filter(Boolean)
      .sort((a, b) => b.updated - a.updated)
      .slice(0, 8);
  } catch {
    return [];
  }
};

function ContinueWatchingRow() {
  const [items, setItems] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const entries = useMemo(readResumeEntries, []);

  useEffect(() => {
    let alive = true;
    if (!entries.length) {
      setLoaded(true);
      return undefined;
    }

    Promise.all(
      entries.map(async (entry) => {
        const details = await movieAPI.getMovieDetails(entry.id);
        if (!details) return null;
        const runtimeSeconds = Number(details.runtime) * 60;
        if (Number.isFinite(runtimeSeconds) && runtimeSeconds > 0) {
          if (entry.position >= runtimeSeconds - 15) return null;
        }
        return {
          ...details,
          resumePosition: entry.position,
          progress:
            Number.isFinite(runtimeSeconds) && runtimeSeconds > 0
              ? entry.position / runtimeSeconds
              : null,
        };
      }),
    )
      .then((results) => {
        if (alive) setItems(results.filter(Boolean));
      })
      .catch(() => {
        if (alive) setItems([]);
      })
      .finally(() => {
        if (alive) setLoaded(true);
      });

    return () => {
      alive = false;
    };
  }, [entries]);

  if (!loaded || items.length === 0) return null;

  return (
    <HomeRow
      title="Continue Watching"
      eyebrow="Pick up where you left off"
      accent="from-cyan-300 to-indigo-500"
      moreLink=""
      loading={false}
    >
      {items.map((item) => (
        <div
          key={`continue-${item.id}`}
          className="w-[152px] flex-shrink-0 sm:w-[168px] md:w-[180px]"
        >
          <HomePosterCard
            item={item}
            mediaType="movie"
            progress={item.progress}
            resumeAt={formatTime(item.resumePosition)}
            to={`/movie/${item.id}/watch`}
          />
        </div>
      ))}
    </HomeRow>
  );
}

function HomeRows() {
  return (
    <>
      <ContinueWatchingRow />
      {homeRowSpecs.map((spec) => (
        <RowSection key={spec.id} spec={spec} />
      ))}
    </>
  );
}

export default HomeRows;
