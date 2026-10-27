import React, { useState, useEffect, useRef, useCallback } from "react";
import { Link } from "react-router-dom";

const rangeSlider = ({ min, max, onChange }) => {
  const [minVal, setMinVal] = useState(min);
  const [maxVal, setMaxVal] = useState(max);
  const minValRef = useRef(min);
  const maxValRef = useRef(max);
  const range = useRef(null);

  // Convert to percentage to position the "active" blue track
  const getPercent = useCallback(
    (value) => Math.round(((value - min) / (max - min)) * 100),
    [min, max]
  );

  // Set width of the range selection whenever min value changes
  useEffect(() => {
    const minPercent = getPercent(minVal);
    const maxPercent = getPercent(maxValRef.current);

    if (range.current) {
      range.current.style.left = `${minPercent}%`;
      range.current.style.width = `${maxPercent - minPercent}%`;
    }
  }, [minVal, getPercent]);

  // Set width of the range selection whenever max value changes
  useEffect(() => {
    const minPercent = getPercent(minValRef.current);
    const maxPercent = getPercent(maxVal);

    if (range.current) {
      range.current.style.width = `${maxPercent - minPercent}%`;
    }
  }, [maxVal, getPercent]);

  return (
    <div className="flex flex-col w-full max-w-md px-4 py-8">
      <div className="flex justify-between mb-6">
        <div className="text-gray-700 font-semibold">
          From: <span className="text-blue-600">{minVal}</span>
        </div>
        <div className="text-gray-700 font-semibold">
          To: <span className="text-blue-600">{maxVal}</span>
        </div>
      </div>

      <div className="relative h-2 flex items-center">
        {/* The underlying gray track */}
        <div className="absolute w-full h-1 bg-gray-200 rounded-full z-0" />

        {/* The active blue track */}
        <div
          ref={range}
          className="absolute h-1 bg-blue-500 rounded-full z-10"
        />

        {/* Left Slider Handle */}
        <input
          type="range"
          min={min}
          max={max}
          value={minVal}
          onChange={(e) => {
            const value = Math.min(Number(e.target.value), maxVal - 1);
            setMinVal(value);
            minValRef.current = value;
            onChange({ min: value, max: maxVal });
          }}
          className="absolute w-full h-0 pointer-events-none appearance-none z-30 outline-none accent-blue-600 custom-slider"
        />

        {/* Right Slider Handle */}
        <input
          type="range"
          min={min}
          max={max}
          value={maxVal}
          onChange={(e) => {
            const value = Math.max(Number(e.target.value), minVal + 1);
            setMaxVal(value);
            maxValRef.current = value;
            onChange({ min: minVal, max: value });
          }}
          className="absolute w-full h-0 pointer-events-none appearance-none z-40 outline-none accent-blue-600 custom-slider"
        />
      </div>
    </div>
  );
};

export default rangeSlider;
