import React, { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { FaArrowRight, FaChevronLeft, FaChevronRight } from "react-icons/fa";

function HomeRow({
  title,
  eyebrow = "",
  accent = "from-cyan-300 to-indigo-600",
  moreLink = "",
  loading = false,
  skeletonCount = 8,
  skeletonVariant = "poster",
  children,
}) {
  const scrollRef = useRef(null);

  const scroll = (direction) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollBy({
      left: direction === "left" ? -Math.round(el.clientWidth * 0.8) : Math.round(el.clientWidth * 0.8),
      behavior: "smooth",
    });
  };

  // Shift + scroll = horizontal scroll, drag = horizontal scroll.
  // A plain wheel still scrolls the page vertically.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return undefined;

    let targetScrollLeft = el.scrollLeft;
    let animationId = null;

    const smoothScroll = () => {
      const diff = targetScrollLeft - el.scrollLeft;
      if (Math.abs(diff) < 0.5) {
        el.scrollLeft = targetScrollLeft;
        return;
      }
      el.scrollLeft += diff * 0.12;
      animationId = requestAnimationFrame(smoothScroll);
    };

    const handleWheel = (e) => {
      if (!e.shiftKey) return;
      e.preventDefault();
      targetScrollLeft += e.deltaY * 3;
      targetScrollLeft = Math.max(
        0,
        Math.min(targetScrollLeft, el.scrollWidth - el.clientWidth),
      );
      cancelAnimationFrame(animationId);
      animationId = requestAnimationFrame(smoothScroll);
    };

    let isDown = false;
    let startX = 0;
    let startScrollLeft = 0;
    let hasDragged = false;

    const onMouseDown = (e) => {
      e.preventDefault();
      isDown = true;
      hasDragged = false;
      startX = e.pageX - el.offsetLeft;
      startScrollLeft = el.scrollLeft;
      el.style.cursor = "grabbing";
    };

    const onMouseUpOrLeave = () => {
      isDown = false;
      el.style.cursor = "grab";
    };

    const onMouseMove = (e) => {
      if (!isDown) return;
      e.preventDefault();
      const x = e.pageX - el.offsetLeft;
      const walk = (x - startX) * 1.5;
      if (Math.abs(walk) > 5) hasDragged = true;
      el.scrollLeft = startScrollLeft - walk;
    };

    const onClickCapture = (e) => {
      if (hasDragged) {
        e.preventDefault();
        e.stopPropagation();
        hasDragged = false;
      }
    };

    el.addEventListener("wheel", handleWheel, { passive: false });
    el.addEventListener("mousedown", onMouseDown);
    el.addEventListener("mouseup", onMouseUpOrLeave);
    el.addEventListener("mouseleave", onMouseUpOrLeave);
    el.addEventListener("mousemove", onMouseMove);
    el.addEventListener("click", onClickCapture, true);

    return () => {
      cancelAnimationFrame(animationId);
      el.removeEventListener("wheel", handleWheel);
      el.removeEventListener("mousedown", onMouseDown);
      el.removeEventListener("mouseup", onMouseUpOrLeave);
      el.removeEventListener("mouseleave", onMouseUpOrLeave);
      el.removeEventListener("mousemove", onMouseMove);
      el.removeEventListener("click", onClickCapture, true);
    };
  }, []);

  return (
    <>
      <div className="mt-10 flex items-center gap-3 md:gap-4">
        <span
          className={`h-7 w-[3px] shrink-0 rounded-full bg-gradient-to-b ${accent} shadow-[0_0_10px_rgba(56,189,248,0.35)]`}
          aria-hidden="true"
        />
        <div className="min-w-0">
          <h2 className="truncate text-md font-bold text-white md:text-xl">
            {title}
          </h2>
          {eyebrow ? (
            <p className="mt-0.5 truncate text-[8.5px] font-semibold uppercase tracking-[0.2em] text-white/35 md:text-[9.5px]">
              {eyebrow}
            </p>
          ) : null}
        </div>
        <div className="mb-1 flex-1 border-b-2 border-dotted border-gray-600 opacity-50" />
        {moreLink ? (
          <Link
            to={moreLink}
            className="group/more flex shrink-0 items-center gap-2 rounded-full px-2 py-1 text-[10pt] font-bold text-[grey] transition hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/70"
          >
            More
            <FaArrowRight className="transition-transform duration-200 group-hover/more:translate-x-0.5" />
          </Link>
        ) : null}
      </div>

      <div className="group relative mb-10 mt-6">
        <button
          type="button"
          onClick={() => scroll("left")}
          aria-label={`Scroll ${title} left`}
          className="absolute left-0 top-0 z-20 flex h-full w-10 cursor-pointer items-center justify-center bg-gradient-to-r from-black/90 to-transparent text-white opacity-0 transition-opacity duration-300 focus:outline-none focus-visible:opacity-100 group-hover:opacity-100 md:w-12"
        >
          <FaChevronLeft size={20} />
        </button>

        <div
          ref={scrollRef}
          className="scrollhide -mt-2 flex gap-4 overflow-x-auto pt-2"
          onDragStart={(e) => e.preventDefault()}
          style={{ cursor: "grab", userSelect: "none", overflowY: "hidden" }}
        >
          {loading
            ? Array.from({ length: skeletonCount }).map((_, index) => (
                <div
                  key={`skeleton-${index}`}
                  className={`img-skeleton flex-shrink-0 rounded-[12px] ${
                    skeletonVariant === "wide"
                      ? "aspect-video w-[280px] sm:w-[320px]"
                      : skeletonVariant === "ranked"
                        ? "aspect-[2/3] w-[270px]"
                        : "aspect-[2/3] w-[152px] sm:w-[168px] md:w-[180px]"
                  }`}
                />
              ))
            : children}
        </div>

        <button
          type="button"
          onClick={() => scroll("right")}
          aria-label={`Scroll ${title} right`}
          className="absolute right-0 top-0 z-20 flex h-full w-10 cursor-pointer items-center justify-center bg-gradient-to-l from-black/90 to-transparent text-white opacity-0 transition-opacity duration-300 focus:outline-none focus-visible:opacity-100 group-hover:opacity-100 md:w-12"
        >
          <FaChevronRight size={20} />
        </button>
      </div>
    </>
  );
}

export default HomeRow;
