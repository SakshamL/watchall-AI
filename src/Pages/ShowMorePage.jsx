// import React, { useState, useEffect, useRef } from "react";
// import { movieAPI } from "../Services/api";
// import MovieCard from "../Components/MovieCard";
// import SearchBar from "../Components/SearchBar";

// const IMGPATH = "https://image.tmdb.org/t/p/w1280";

// function ShowMorePage(props) {
//   const newPage = 1;
//   const startYear = 1990;

//   // const prevPathRef = useRef(location.pathname);

//   const [movies, setMovies] = useState([]);
//   const [genres, setGenres] = useState([]);
//   const [selectedGenres, setSelectedGenres] = useState(
//     sessionStorage.getItem("genre-hi")
//       ? sessionStorage.getItem("genre-hi")
//       : "",
//   );
//   const prevGenreRef = useRef(sessionStorage.getItem("genre-hi") || "");
//   const [pageNo, setpageNo] = useState(1);
//   const [currPage, setcurrPage] = useState(
//     sessionStorage.getItem("page") ? sessionStorage.getItem("page") : 1,
//   );
//   const [yearsList, setyearsList] = useState([]);
//   const [selectedYear, setselectedYear] = useState(
//     sessionStorage.getItem("year")
//       ? sessionStorage.getItem("year")
//       : new Date().getFullYear().toString(),
//   );

//   useEffect(() => {
//     getMovies();
//     getGenres();
//     yearsDropdown();
//     sessionStorage.setItem("genre-hi", selectedGenres);
//   }, []);

//   useEffect(() => {
//     getMovies();
//     getGenres();
//   }, [newPage]);

//   useEffect(() => {
//     sessionStorage.setItem("genre-hi", selectedGenres);
//     if (prevGenreRef.current !== selectedGenres) {
//       // console.log(
//       //   `Value Changed! Old: ${prevGenreRef.current}, New: ${selectedGenres}`,
//       // );
//       prevGenreRef.current = selectedGenres;
//       sessionStorage.setItem("page", 1);
//     }
//     getMovies();
//   }, [selectedGenres]);

//   useEffect(() => {
//     getMovies();
//   }, [currPage]);

//   useEffect(() => {
//     sessionStorage.setItem("year", selectedYear);
//     getMovies();
//   }, [selectedYear]);

//   // ----------------------------------------------------------------------
//   // useEffect(() => {
//   //   const currentPath = location.pathname;
//   //   const previousPath = prevPathRef.current;

//   //   // Check if we are moving between the listing pages
//   //   const isNavigatingBetweenLists =
//   //     (previousPath.includes("hindi-movies") &&
//   //       !currentPath.includes("hindi-movies")) ||
//   //     (!previousPath.includes("hindi-movies") &&
//   //       currentPath.includes("hindi-movies"));

//   //   if (isNavigatingBetweenLists) {
//   //     sessionStorage.removeItem("genre");
//   //     sessionStorage.removeItem("page");
//   //     sessionStorage.removeItem("year");
//   //     setSelectedGenres("");
//   //     setselectedYear(new Date().getFullYear().toString());
//   //     setcurrPage(1);
//   //   }

//   //   // Update the ref for the next change
//   //   prevPathRef.current = currentPath;
//   // }, [location.pathname]);

//   // -----------------------------------------------------------------------

//   const yearsDropdown = () => {
//     let todayYear = new Date().getFullYear();
//     let tempYearsList = [];
//     while (todayYear >= startYear) {
//       tempYearsList.push(todayYear);
//       todayYear = todayYear - 1;
//     }
//     setyearsList(tempYearsList);
//   };

//   // Debounce: Wait 500ms after the user input
//   // useEffect(() => {
//   //   const delayDebounceFn = setTimeout(() => {
//   //     console.log(`Fetching TMDb movies from ${range.min} to ${range.max}...`);

//   //     const startDate = `${range.min}-01-01`;
//   //     const endDate = `${range.max}-12-31`;

//   //     // Call your API fetch function here
//   //     // fetchMovies(startDate, endDate);
//   //   }, 500);

//   //   return () => clearTimeout(delayDebounceFn);
//   // }, [range]);

//   const getMovies = async () => {
//     // const response = await fetch(movies_api + newPage + movies_api_2);
//     if (props.language == "hi") {
//       const response = await movieAPI.getAllHindiMovies(
//         getpagefromSession(),
//         getgenrefromSession(),
//         getyearfromSession(),
//       );
//       setMovies(response.results);
//       setpageNo(response.total_pages);
//     }
//     if (props.language == "en") {
//       const response = await movieAPI.getAllEnglishMovies(
//         getpagefromSession(),
//         getgenrefromSession(),
//         getyearfromSession(),
//       );
//       setMovies(response.results);
//       setpageNo(response.total_pages);
//     }
//   };

//   const getGenres = async () => {
//     const response = await movieAPI.getGenres();
//     // const response = await fetch(movie_genres);
//     // const responseJSON = await response.json();
//     setGenres(response.genres);

//     // console.log(responseJSON.genres)
//   };

//   const checkSelectedGenres = async (e) => {
//     const id = e.target.value;
//     setSelectedGenres([id]);
//     setcurrPage(pageNo > 0 ? 1 : 0);
//   };

//   const getgenrefromSession = () => {
//     const gen = sessionStorage.getItem("genre-hi");
//     return gen ? gen : "";
//   };

//   const getyearfromSession = () => {
//     const yr = sessionStorage.getItem("year");
//     return yr ? yr : "";
//   };

//   const getpagefromSession = () => {
//     const pg = sessionStorage.getItem("page");
//     return pg ? pg : 1;
//   };

//   const checkSelectedYear = (e) => {
//     const id = e.target.value;
//     setselectedYear([id]);
//     setcurrPage(pageNo > 0 ? 1 : 0);
//   };

//   return (
//     <>
//       {props.language == "hi" ? (
//         <title>{"Hindi Movies - WatchAll"}</title>
//       ) : props.language == "en" ? (
//         <title>{"English Movies - WatchAll"}</title>
//       ) : (
//         <title>{"All Movies - WatchAll"}</title>
//       )}
//       {/* <div className="text-[black]  flex justify-center">
//           //{" "}
//           <button
//             className="bg-white cursor-pointer mt-10 p-2"
//             onClick={(e) => {
//               if (movies.length > 0) {
//                 setNewPage(newPage + 1);
//                 setprevpage(newPage);
//               }
//             }}
//           >
//             Page {newPage}
//           </button>
//         </div> */}
//       {/* ---------------------------------------------------------------------------------- */}

//       <div className="w-full">
//         <SearchBar />
//         <div className="flex flex-col gap-5 justify-center items-center md:flex-row md:gap-0 mt-5">
//           <div className="w-full flex items-center justify-center mt-3 md:mt-0 md:flex-row md:w-[30%]">
//             <p className="mr-3 flex-1 md:flex-0 md:mr-0">Year</p>
//             <select
//               name="year"
//               id="year"
//               value={selectedYear}
//               className="border-2  flex-5 md:flex-1 rounded border-[#ffffff26] outline-0 w-full md:w-fit md:mx-3"
//               onChange={checkSelectedYear}
//             >
//               {yearsList.map((year) => {
//                 return (
//                   <option key={year} value={year} className="text-black">
//                     {year}
//                   </option>
//                 );
//               })}
//             </select>
//           </div>
//           <div className="w-full flex items-center justify-center md:flex-row md:w-[30%]">
//             <p className="mr-3 flex-1 md:flex-0 md:mr-2">Genres </p>
//             <select
//               name="genre"
//               id="genre"
//               value={selectedGenres}
//               onChange={checkSelectedGenres}
//               className="border-2 flex-5 md:flex-1 rounded border-[#ffffff26] outline-0 w-full "
//             >
//               <option value="" className="text-black">
//                 All
//               </option>
//               {genres.map((gen) => (
//                 // <p key={gen.id}>{gen.name}</p>
//                 <option
//                   value={gen.id}
//                   id={gen.id}
//                   className=" text-black"
//                   key={gen.id}
//                 >
//                   {gen.name}
//                 </option>
//               ))}
//             </select>
//           </div>
//           <div className="w-full flex items-center justify-center md:justify-end md:flex-row md:w-[50%]">
//             <p className="md:ml-3">
//               Page: {currPage} of {pageNo}
//             </p>
//             <button
//               className={`px-5 border-1 rounded border-amber-50 mx-2 ${
//                 currPage <= 1 ? "cursor-not-allowed" : "cursor-pointer"
//               }`}
//               onClick={() => {
//                 if (currPage > 1) {
//                   setcurrPage(Number(currPage) - 1);
//                   sessionStorage.setItem("page", Number(currPage) - 1);
//                 }
//               }}
//             >
//               Prev
//             </button>
//             <button
//               className={`px-5 border-1 rounded border-amber-50 mx-2 ${
//                 currPage >= pageNo ? "cursor-not-allowed" : "cursor-pointer"
//               }`}
//               onClick={() => {
//                 if (currPage < pageNo) {
//                   setcurrPage(Number(currPage) + 1);
//                   sessionStorage.setItem("page", Number(currPage) + 1);
//                 }
//               }}
//             >
//               Next
//             </button>
//           </div>
//         </div>

//         <div className=" grid grid-cols-2 gap-6 mt-10 mb-10 md:grid-cols-4 lg:grid-cols-7 ">
//           {movies.map((movie) => (
//             <div
//               key={movie.id}
//               className="w-full h-full hover:scale-[1.1] transition duration-150"
//             >
//               {/* <div
//                   key={movie.id}
//                   className="w-[15%] h-[15%]  hover:scale-[1.1] transition duration-100"
//                 > */}
//               <MovieCard
//                 key={movie.id}
//                 id={movie.id}
//                 title={movie.title}
//                 poster_path={
//                   movie.poster_path === null
//                     ? `/no-poster.jpg`
//                     : IMGPATH + movie.poster_path
//                 }
//                 release_date={movie.release_date}
//                 vote={movie.vote_average}
//                 lang={movie.original_language}
//               />
//             </div>
//           ))}
//         </div>
//         <div className="flex justify-center mb-20">
//           <p className="ml-3">
//             Page: {currPage} of {pageNo}
//           </p>
//           <button
//             className={`px-5 border-1 rounded border-amber-50 mx-2 ${
//               currPage <= 1 ? "cursor-not-allowed" : "cursor-pointer"
//             }`}
//             onClick={() => {
//               if (currPage > 1) {
//                 setcurrPage(Number(currPage) - 1);
//                 sessionStorage.setItem("page", Number(currPage) - 1);
//               }
//             }}
//           >
//             Prev
//           </button>
//           <button
//             className={`px-5 border-1 rounded border-amber-50 mx-2 ${
//               currPage >= pageNo ? "cursor-not-allowed" : "cursor-pointer"
//             }`}
//             onClick={() => {
//               if (currPage < pageNo) {
//                 setcurrPage(Number(currPage) + 1);
//                 sessionStorage.setItem("page", Number(currPage) + 1);
//               }
//             }}
//           >
//             Next
//           </button>
//         </div>
//       </div>
//     </>
//   );
// }

// export default ShowMorePage;
