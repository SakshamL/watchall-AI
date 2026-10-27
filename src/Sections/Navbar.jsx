import React, { useState } from "react";
import { NavLink, Link, useLocation } from "react-router-dom";

import { AiOutlineMenu, AiOutlineClose } from "react-icons/ai";

function Navbar() {
  const location = useLocation();
  const [clicked, setClicked] = useState(false);

  function handleResNavbar() {
    setClicked(!clicked);
    if (!clicked) {
      document.getElementById("navbar").classList.add("responsive-navbar");
      // document.body.style.overflow = "hidden";
    } else {
      document.getElementById("navbar").classList.remove("responsive-navbar");
      // document.body.style.overflow = "auto";
    }
  }

  return (
    <div className="flex items-center mt-3 lg:justify-between">
      <Link to="/" className="w-[200px] ml-[3%]   cursor-pointer">
        <img src="/logo_2.png" alt="logo" loading="eager" fetchpriority="high" />
      </Link>
      <div
        className={`text-white ${location.pathname.endsWith("/watch") ? "hidden" : "block"} lg:hidden text-2xl cursor-pointer z-20 absolute right-5 top-10`}
        onClick={handleResNavbar}
      >
        {!clicked ? <AiOutlineMenu /> : <AiOutlineClose />}
      </div>
      <div>
        {/* <ul
          id="navbar"
          className="max-md:hidden flex text-white  mr-30 max-md:flex-col max-md:absolute max-md:left-0 max-md:top-0 max-md:w-full max-md:h-full max-md:items-center max-md:bg-[#1f2138] max-md:z-10 max-md:pt-20;"
        > */}
        <ul id="navbar" className="hidden text-white max-lg:flex-col lg:flex ">
          <li className="">
            <Link
              to="/"
              onClick={handleResNavbar}
              className="max-lg:pr-20 max-lg:pl-20 mr-5 font-semibold navbar-menu-items"
            >
              Home
            </Link>
          </li>
          <li className="">
            <Link
              to="/movies"
              onClick={handleResNavbar}
              className="max-lg:pr-20 max-lg:pl-20 mr-5 font-semibold navbar-menu-items"
            >
              Movies
            </Link>
          </li>
          <li className="">
            <Link
              to="/tv"
              onClick={handleResNavbar}
              className="max-lg:pr-20 max-lg:pl-20 mr-5 font-semibold navbar-menu-items"
            >
              TV Shows
            </Link>
          </li>
          {/* <li className="">
            <NavLink
              to="/genres"
              onClick={handleResNavbar}
              className="max-lg:pr-20 max-lg:pl-20 mr-5 navbar-menu-items"
            >
              Genres
            </NavLink>
          </li> */}
        </ul>
      </div>
    </div>
  );
}

export default Navbar;
