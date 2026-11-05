package com.watchall.core.net

import com.google.gson.annotations.SerializedName

internal data class TmdbPageDto(
    val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1,
    @SerializedName("total_results") val totalResults: Int = 0,
    val results: List<TmdbMediaDto> = emptyList(),
)

internal data class TmdbMediaDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    val popularity: Double = 0.0,
    val adult: Boolean = false,
)

internal data class TmdbGenreDto(
    val id: Int = 0,
    val name: String = "",
)

internal data class TmdbGenresDto(
    val genres: List<TmdbGenreDto> = emptyList(),
)

internal data class TmdbDetailsDto(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    val runtime: Int? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    val status: String? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int = 0,
    val seasons: List<TmdbSeasonDto> = emptyList(),
    val credits: TmdbCreditsDto? = null,
    val videos: TmdbVideosDto? = null,
    @SerializedName("release_dates") val releaseDates: TmdbReleaseDatesDto? = null,
    @SerializedName("content_ratings") val contentRatings: TmdbContentRatingsDto? = null,
    val recommendations: TmdbPageDto? = null,
)

internal data class TmdbSeasonDto(
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
)

internal data class TmdbEpisodeDto(
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
)

internal data class TmdbSeasonDetailsDto(
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)

internal data class TmdbCreditsDto(
    val cast: List<TmdbCastDto> = emptyList(),
)

internal data class TmdbCastDto(
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
    @SerializedName("profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

internal data class TmdbCombinedCreditsDto(
    val cast: List<TmdbMediaDto> = emptyList(),
)

internal data class TmdbVideosDto(
    val results: List<TmdbVideoDto> = emptyList(),
)

internal data class TmdbVideoDto(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    @SerializedName("iso_639_1") val language: String? = null,
)

internal data class TmdbReleaseDatesDto(
    val results: List<TmdbReleaseCountryDto> = emptyList(),
)

internal data class TmdbReleaseCountryDto(
    @SerializedName("iso_3166_1") val country: String = "",
    @SerializedName("release_dates") val releaseDates: List<TmdbReleaseDateDto> = emptyList(),
)

internal data class TmdbReleaseDateDto(
    val certification: String? = null,
    val type: Int = 0,
)

internal data class TmdbContentRatingsDto(
    val results: List<TmdbContentRatingDto> = emptyList(),
)

internal data class TmdbContentRatingDto(
    @SerializedName("iso_3166_1") val country: String = "",
    val rating: String? = null,
)
