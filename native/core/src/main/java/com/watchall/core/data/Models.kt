package com.watchall.core.data

enum class MediaType(val apiValue: String, val label: String) {
    MOVIE("movie", "Movie"),
    TV("tv", "TV Show");

    companion object {
        fun fromApi(value: String?): MediaType? = when (value?.lowercase()) {
            "movie" -> MOVIE
            "tv" -> TV
            else -> null
        }
    }
}

enum class SourceList(
    val apiId: String,
    val displayName: String,
    val hint: String,
    val supportsTv: Boolean,
) {
    SERVER_ONE("viduki", "Server List 1", "Recommended", true),
    SERVER_TWO("vidnest", "Server List 2", "Alternative", false),
    SERVER_THREE("vidsrc", "Server List 3", "VidSrc (embed)", true);

    companion object {
        fun fromApi(value: String?): SourceList =
            when (value) {
                SERVER_TWO.apiId -> SERVER_TWO
                SERVER_THREE.apiId -> SERVER_THREE
                else -> SERVER_ONE
            }

        fun available(mediaType: MediaType): List<SourceList> =
            entries.filter { it.supportsTv || mediaType == MediaType.MOVIE }
    }
}

data class MediaSummary(
    val id: Int,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double = 0.0,
    val year: String = "",
    val mediaType: MediaType = MediaType.MOVIE,
    val originalLanguage: String = "",
    val genreIds: List<Int> = emptyList(),
) {
    val key: String get() = "${mediaType.apiValue}-$id"
    val displayTitle: String get() = title.ifBlank { "Untitled" }
}

data class Genre(
    val id: Int,
    val name: String,
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profilePath: String?,
)

data class SeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val episodeCount: Int,
    val posterPath: String?,
    val airDate: String,
)

data class EpisodeSummary(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    val stillPath: String?,
    val runtimeMinutes: Int,
    val airDate: String,
    val rating: Double,
) {
    val code: String get() = "S${seasonNumber.toString().padStart(2, '0')} E${episodeNumber.toString().padStart(2, '0')}"
}

data class SeasonDetails(
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val episodes: List<EpisodeSummary>,
)

data class MediaDetails(
    val id: Int,
    val title: String,
    val overview: String,
    val tagline: String,
    val mediaType: MediaType,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double,
    val voteCount: Int,
    val year: String,
    val releaseDate: String,
    val runtimeMinutes: Int,
    val genres: List<Genre>,
    val originalLanguage: String,
    val status: String,
    val numberOfSeasons: Int,
    val numberOfEpisodes: Int,
    val seasons: List<SeasonSummary>,
    val cast: List<CastMember>,
    val trailerKey: String?,
    val certification: String,
    val recommendations: List<MediaSummary>,
) {
    val displayTitle: String get() = title.ifBlank { "Untitled" }
    val mediaKey: String get() = "${mediaType.apiValue}-$id"
}

data class HomeRowQuery(
    val mediaType: MediaType,
    val path: String? = null,
    val languages: List<String> = emptyList(),
    val releaseTypes: String? = null,
    val releaseWindowDays: Int? = null,
    val sortBy: String = "popularity.desc",
    val minVoteCount: Int = 0,
    val minRating: Double = 0.0,
    val region: String? = "IN",
)

data class HomeRow(
    val id: String,
    val title: String,
    val eyebrow: String = "",
    val items: List<MediaSummary>,
    val query: HomeRowQuery? = null,
    val ranked: Boolean = false,
)

data class HomeFeed(
    val featured: List<MediaSummary>,
    val rows: List<HomeRow>,
)

data class ServerInfo(
    val name: String,
    val label: String,
    val language: String,
) {
    val displayName: String
        get() = if (name.equals("vidnest", ignoreCase = true)) "Server List 2" else label.ifBlank { name }
}

data class StreamVariant(
    val playbackUrl: String,
    val originalUrl: String = playbackUrl,
    val type: String = "mp4",
    val quality: Int = 0,
    val codec: String = "",
    val verified: Boolean = false,
) {
    val label: String get() = if (quality > 0) "${quality}p" else type.uppercase()
}

data class CaptionTrack(
    val url: String,
    val label: String = "Subtitles",
    val language: String = "",
    val format: String = "vtt",
)

data class StreamResult(
    val playbackUrl: String,
    val originalUrl: String,
    val type: String,
    val server: String?,
    val source: SourceList,
    val referer: String,
    val variants: List<StreamVariant> = emptyList(),
    val variantIndex: Int = 0,
    val captionsUrl: String = "",
    val captions: List<CaptionTrack> = emptyList(),
) {
    val allVariants: List<StreamVariant>
        get() = if (variants.isEmpty()) listOf(StreamVariant(playbackUrl, originalUrl, type)) else variants

    val activeVariant: StreamVariant
        get() = allVariants.getOrElse(variantIndex.coerceIn(0, allVariants.lastIndex)) { allVariants.first() }

    fun withVariant(index: Int): StreamResult =
        copy(variantIndex = index.coerceIn(0, allVariants.lastIndex.coerceAtLeast(0)))
}

data class ResumeEntry(
    val mediaKey: String,
    val title: String,
    val subtitle: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: MediaType,
    val mediaId: Int = 0,
    val originalLanguage: String = "",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

fun ResumeEntry.toPlayerRequest(initialSource: SourceList = SourceList.SERVER_ONE): PlayerRequest? {
    val resolvedId = mediaId.takeIf { it > 0 }
        ?: mediaKey.substringAfter('-').substringBefore('-').toIntOrNull()
        ?: return null
    return PlayerRequest(
        mediaId = resolvedId,
        title = title,
        mediaType = mediaType,
        posterPath = posterPath,
        backdropPath = backdropPath,
        originalLanguage = originalLanguage,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        initialSource = initialSource,
    )
}

data class WatchlistItem(
    val media: MediaSummary,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * A title the viewer finished (or watched almost to the end). Movies are kept
 * as a single entry; TV shows keep the episodes that were completed so the
 * count stays accurate when the same episode is replayed.
 */
data class WatchedEntry(
    val mediaKey: String,
    val mediaId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val originalLanguage: String = "",
    val year: String = "",
    val lastEpisodeLabel: String? = null,
    val episodes: List<String> = emptyList(),
    val watchedAt: Long = System.currentTimeMillis(),
) {
    val displayTitle: String get() = title.ifBlank { "Untitled" }

    val episodeCount: Int get() = episodes.size

    val subtitle: String
        get() = when (mediaType) {
            MediaType.MOVIE -> "Movie · Watched"
            MediaType.TV -> buildString {
                append(lastEpisodeLabel?.takeIf { it.isNotBlank() } ?: "TV Show")
                if (episodeCount > 1) append(" · $episodeCount episodes")
                append(" · Watched")
            }
        }

    fun toSummary(): MediaSummary = MediaSummary(
        id = mediaId,
        title = title,
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = year,
        mediaType = mediaType,
        originalLanguage = originalLanguage,
    )
}

data class AppSettings(
    val apiBaseUrl: String,
    val defaultApiBaseUrl: String,
) {
    val usesDefaultServer: Boolean get() = apiBaseUrl == defaultApiBaseUrl
}

data class TrackChoice(
    val id: String,
    val label: String,
    val language: String? = null,
    val height: Int? = null,
)

data class PlaybackFailure(
    val title: String,
    val message: String,
    val code: String = "",
)

data class PlayerSourceRequest(
    val mediaId: Int,
    val mediaType: MediaType,
    val originalLanguage: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

data class PlayerRequest(
    val mediaId: Int,
    val title: String,
    val mediaType: MediaType,
    val posterPath: String?,
    val backdropPath: String?,
    val originalLanguage: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val initialSource: SourceList = SourceList.SERVER_ONE,
    val initialServer: String? = null,
) {
    val mediaKey: String
        get() = when (mediaType) {
            MediaType.MOVIE -> "movie-$mediaId"
            MediaType.TV -> "tv-$mediaId-s${seasonNumber ?: 1}-e${episodeNumber ?: 1}"
        }

    val subtitle: String
        get() = when (mediaType) {
            MediaType.MOVIE -> "Movie"
            MediaType.TV -> "S${(seasonNumber ?: 1).toString().padStart(2, '0')} E${(episodeNumber ?: 1).toString().padStart(2, '0')}" +
                (episodeTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        }
}
