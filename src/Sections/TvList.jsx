import React, { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { tv_shows } from "../Services/api";
import TVCard from "../Components/TVCard";
import { FaChevronLeft, FaChevronRight } from "react-icons/fa";

const IMGPATH = "https://image.tmdb.org/t/p/w342"; // w342 is enough for card thumbnails

function TvList() {
  const [tv, setTv] = useState([]);
  const scrollRef = useRef(null);

  useEffect(() => {
    getTvShows();
  }, []);

  const getTvShows = async () => {
    const response = await fetch(tv_shows);
    const responseJSON = await response.json();
    setTv(responseJSON.results);
  };

  // Scroll left or right by 500px on arrow click
  const scroll = (direction) => {
    scrollRef.current.scrollBy({
      left: direction === "left" ? -500 : 500,
      behavior: "smooth",
    });
  };

  // Shift + Scroll = horizontal scroll | Normal scroll = page scrolls as usual
  // Mouse drag = drag to scroll horizontally
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    // Smooth scroll target using lerp (linear interpolation)
    let targetScrollLeft = el.scrollLeft;
    let animationId = null;

    const smoothScroll = () => {
      const diff = targetScrollLeft - el.scrollLeft;
      if (Math.abs(diff) < 0.5) {
        el.scrollLeft = targetScrollLeft; // snap to final position
        return;
      }
      el.scrollLeft += diff * 0.12; // 0.12 = how fast it catches up (higher = faster)
      animationId = requestAnimationFrame(smoothScroll);
    };

    const handleWheel = (e) => {
      if (e.shiftKey) {
        e.preventDefault();
        targetScrollLeft += e.deltaY * 3;
        // Keep target within scroll bounds
        targetScrollLeft = Math.max(0, Math.min(targetScrollLeft, el.scrollWidth - el.clientWidth));
        cancelAnimationFrame(animationId);
        animationId = requestAnimationFrame(smoothScroll);
      }
      // No shift = page scrolls normally
    };

    // Mouse drag to scroll
    let isDown = false;
    let startX = 0;
    let startScrollLeft = 0;
    let hasDragged = false; // tracks if mouse actually moved during drag

    const onMouseDown = (e) => {
      e.preventDefault(); // stops browser native image drag and text selection
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
      if (Math.abs(walk) > 5) hasDragged = true; // only count as drag if moved more than 5px
      el.scrollLeft = startScrollLeft - walk;
    };

    // Block click on cards if a drag just happened (capture phase = fires before NavLink)
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
    el.addEventListener("click", onClickCapture, true); // true = capture phase

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
      {/* Section Heading */}
      <div className="flex gap-5 justify-between items-center mt-10">
        <h2 className="text-white font-bold text-md md:text-xl">Trending TV Shows</h2>
        <div className="flex-1 border-b-2 border-dotted opacity-50 border-gray-600 mb-1"></div>
        <Link to="/tv">
          <h2 className="text-[grey] font-bold text-[10pt] flex items-center gap-3">
            More <FaChevronRight />
          </h2>
        </Link>
      </div>

      {/* Slider Container */}
      <div className="relative mt-6 mb-10 group">

        {/* Left Arrow — full height, overlaps content, visible only on row hover */}
        <button
          onClick={() => scroll("left")}
          className="absolute left-0 top-0 h-full w-12 z-20 cursor-pointer flex items-center justify-center text-white bg-gradient-to-r from-black/90 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300"
        >
          <FaChevronLeft size={20} />
        </button>

        {/* Scrollable TV Shows Row — 1 row, drag or Shift+scroll, no scrollbar visible */}
        <div
          ref={scrollRef}
          className="flex gap-4 overflow-x-auto scrollhide"
          onDragStart={(e) => e.preventDefault()}
          style={{ cursor: "grab", userSelect: "none", overflowY: "hidden" }}
        >
          {tv.map((show) => (
            <div
              key={show.id}
              className="flex-shrink-0 w-[155px] hover:scale-[1.05] hover:z-10 transition duration-150"
            >
              <TVCard
                id={show.id}
                name={show.name}
                poster_path={
                  show.poster_path === null
                    ? `no-poster.jpg`
                    : IMGPATH + show.poster_path
                }
                first_air_date={show.first_air_date}
                vote={show.vote_average}
              />
            </div>
          ))}
        </div>

        {/* Right Arrow — full height, overlaps content, visible only on row hover */}
        <button
          onClick={() => scroll("right")}
          className="absolute right-0 top-0 h-full w-12 z-20 cursor-pointer flex items-center justify-center text-white bg-gradient-to-l from-black/90 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300"
        >
          <FaChevronRight size={20} />
        </button>

      </div>
    </>
  );
}

export default TvList;
