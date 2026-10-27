import React from "react";
import { NavLink } from "react-router-dom";

const SearchCard = (props) => {
  return (
    <NavLink to={`/movie/${props.id}/${props.title.toLowerCase().replace(/[&\/\\#, +()$~%.'":*?<>{}]/g, "-")}`}>
      <div className="w-full h-full relative text-[70%] text-[#ebebeb] font-bold ">
        <div className="movie-card-gradient w-full h-full absolute opacity-[0.5px] top-0 left-0 rounded-xl"></div>
        <img
          src={props.poster_path}
          alt={props.title}
          loading="lazy"
          decoding="async"
          className="rounded-xl w-full h-full shadow-[0px_4px_5px_0px_rgba(0,_0,_0,_0.25)]"
          onError={(e) => { e.target.src = "/placeholder.png"; }}
        />
        <p className="absolute bottom-[10px] left-[10px] font-inter">
          {props.title}
        </p>
        <p className="">{props.release_date}</p>
        <p className="absolute top-0 right-[5px] flex items-center">
          {props.vote ? (
            <>
              {props.vote.toFixed(2)}
              <img src="/star.png" alt="star" loading="lazy" className="w-[20px] h-full" />
            </>
          ) : null}
        </p>
      </div>
    </NavLink>
  );
};

export default SearchCard;
