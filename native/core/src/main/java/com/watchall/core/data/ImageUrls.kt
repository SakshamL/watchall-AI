package com.watchall.core.data

object ImageUrls {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun poster(path: String?, size: String = "w500"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE$size$it" }

    fun backdrop(path: String?, size: String = "w1280"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE$size$it" }

    fun profile(path: String?, size: String = "w185"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE$size$it" }

    fun still(path: String?, size: String = "w300"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE$size$it" }
}
