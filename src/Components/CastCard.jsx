import React from "react";
import { Link } from "react-router-dom";

function CastCard(props) {
  return (
    <Link className="block h-full focus:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300/70 focus-visible:ring-offset-2 focus-visible:ring-offset-black rounded-[14px]">
      <div className="flex h-full flex-col overflow-hidden rounded-[14px] border border-[#6f9fe656] bg-[#0b1020] transition duration-150 hover:border-[#6f9fe6a8]">
        <div className="img-skeleton aspect-[2/3] w-full overflow-hidden">
          <img
            src={props.image}
            alt={props.name}
            loading="lazy"
            decoding="async"
            className="h-full w-full object-cover img-fade-in"
            onLoad={(e) => e.target.classList.add("loaded")}
            onError={(e) => {
              e.target.onerror = null;
              e.target.src = "/placeholder.png";
            }}
          />
        </div>
        <div className="flex flex-1 flex-col justify-start px-2 pb-3 pt-2 text-center">
          <p className="line-clamp-2 text-[13px] font-semibold leading-tight krub-regular">
            {props.name}
          </p>
          {props.char ? (
            <p className="mt-1 line-clamp-2 text-[11.5px] leading-tight text-[#ffffff7e] krub-regular">
              {props.char}
            </p>
          ) : null}
        </div>
      </div>
    </Link>
  );
}

export default CastCard;
