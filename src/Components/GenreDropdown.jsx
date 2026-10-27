import React, { useState, useEffect } from "react";
// import { movie_genres } from "../Services/api";
import { movieAPI } from "../Services/api";

const GenreDropdown = ({ options, onSelect }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [selectedOption, setSelectedOption] = useState(null);
  const [genres, setGenres] = useState([]);

  const toggleDropdown = () => setIsOpen(!isOpen);

  useEffect(() => {
    getGenres();
  }, []);

  const handleOptionClick = (option) => {
    setSelectedOption(options.name);
    setIsOpen(false);
    if (onSelect) onSelect(option);
  };

  const getGenres = async () => {
    const response = await movieAPI.getGenres();
    // const response = await fetch(movie_genres);
    // const responseJSON = await response.json();
    setGenres(response.genres);
    // console.log(responseJSON.genres)
  };

  return (
    <div className="relative inline-block text-left w-64">
      {/* The Button / Trigger */}
      <button
        onClick={toggleDropdown}
        className="flex items-center justify-between w-full px-4 py-2 text-sm font-medium text-white bg-gray-800 border border-gray-700 rounded-md hover:bg-gray-700 focus:outline-none"
      >
        {selectedOption ? selectedOption.name : "Select Genre"}
        <svg
          className={`w-5 h-5 ml-2 transition-transform ${
            isOpen ? "rotate-180" : ""
          }`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2"
            d="M19 9l-7 7-7-7"
          />
        </svg>
      </button>

      {/* The Menu Items */}
      {isOpen && (
        <div className="absolute right-0 z-10 w-full mt-2 origin-top-right bg-gray-900 border border-gray-700 rounded-md shadow-xl ring-1 ring-black ring-opacity-5">
          <div className="py-1">
            {options.map((option) => (
              <button
                key={option.id}
                value={option.id}
                onClick={() => {
                  handleOptionClick(option.id);
                }}
                className="block w-full px-4 py-2 text-sm text-left text-gray-300 hover:bg-blue-600 hover:text-white transition-colors"
              >
                {option.name}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default GenreDropdown;
