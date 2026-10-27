import React from "react";
import { NavLink } from "react-router-dom";

function MovieCard(props) {
  return (
    <NavLink
      to={`/movie/${props.id}/${props.title
        .toLowerCase()
        .replace(/[&\/\\#, +()$~%.'":*?<>{}]/g, "-")}`}
    >
      <div className="w-full h-full relative">
        <div className="movie-card-gradient w-full h-full absolute opacity-[0.5px] top-0 left-0 "></div>
        <div className="img-skeleton w-full h-full rounded-[12px]">
          <img
            src={props.poster_path}
            alt={props.title}
            loading="lazy"
            decoding="async"
            className="w-full h-full rounded-[12px] shadow-[0px_4px_5px_0px_rgba(0,_0,_0,_0.25)] img-fade-in"
            onLoad={(e) => e.target.classList.add("loaded")}
            onError={(e) => { e.target.src = "/placeholder.png"; }}
          />
        </div>

        <p
          className={` text-white absolute left-[3px] bottom-[10px] ${
            props.searchedCard ? "text-[70%]" : "text-[75%] sm:text-[90%]"
          } font-bold font-inter`}
        >
          {props.title.length > 18
            ? props.title.substr(0, 18) + ".."
            : props.title}
        </p>
        <p
          className={`text-white absolute left-[10px] bottom-[5px] ${
            props.searchedCard ? "text-[60%]" : "text-[75%] sm:text-[90%]"
          } font-inria-sans`}
        >
          {props.release_date ? props.release_date.slice(0, 4) : null}

          {props.media_type}
        </p>
        <div className="absolute top-1 right-1 bg-[#4c1fb6] font-bold text-white text-[8pt] sm:text-[10pt]  rounded py-0 px-1">
          {props.lang}
        </div>
        {/* <p className="text-white absolute right-[10px] bottom-[5px] text-[75%] font-inria-sans flex items-center">
          {props.vote ? (
            <>
              {props.vote.toFixed(2)}
              <img src="/star.png" alt="star" className="w-[10px] h-full" />
            </>
          ) : null}
        </p> */}
      </div>
    </NavLink>
  );

  // return (
  //   <div className=" text-white flex flex-col w-full h-full  brightness-[1.25] rounded-[10px] border-[#80808000] border-[5px]  focus:scale-[1.3]  hover:scale-[1.1] hover:border-[5px] hover:border-[#bdbdbde9] hover:z-10 focus:z-10 transition duration-200  max-sm:w-[45%] max-sm:hover:scale-[1.1] max-md:w-[30%] max-md:hover:scale-[1.1] max-lg:w-[22%] max-lg:hover:scale-[1.1]">
  //     <Link
  //       to={`/movie/${props.id}`}
  //       className=" rounded-[10px] focus:outline-[5px] focus:outline-[#bdbdbde9] focus:rounded focus:scale-[1.1] transition duration-200"
  //     >
  //       <img
  //         src={props.poster_path}
  //         alt={props.title}
  //         className="rounded-tr-[5px] rounded-tl-[5px]"
  //       />
  //       <p className="p-2 bg-blue-600 rounded-bl-[10px] rounded-br-[10px] flex justify-start title-bg-gradient overflow-hidden truncate">
  //         {props.title}
  //       </p>
  //     </Link>
  //   </div>
  // )
}

export default MovieCard;
