import React from "react";
import { NavLink } from "react-router-dom";

function PersonCard(props) {
  return (
    <NavLink to={``}>
      <div className="w-full h-full relative">
        <div className="movie-card-gradient w-full h-full absolute opacity-[0.5px] top-0 left-0 "></div>
        <img
          src={props.poster_path}
          alt={props.name}
          loading="lazy"
          decoding="async"
          className="w-full h-full rounded-[12px] shadow-[0px_4px_5px_0px_rgba(0,_0,_0,_0.25)] "
          onError={(e) => { e.target.src = "/placeholder.png"; }}
        />
        <p
          className={`text-white absolute left-[10px] bottom-[25px] ${
            props.searchedCard ? "text-[70%]" : "text-[90%]"
          } font-bold font-inter`}
        >
          {props.name}
        </p>
        <p
          className={`text-white absolute left-[10px] bottom-[5px] ${
            props.searchedCard ? "text-[60%]" : "text-[90%]"
          } font-inria-sans`}
        >
          {props.media_type}
        </p>
      </div>
    </NavLink>
  );
}

export default PersonCard;
