package com.watchall.core.net

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.watchall.core.BuildConfig
import com.watchall.core.data.CastMember
import com.watchall.core.data.EpisodeSummary
import com.watchall.core.data.Genre
import com.watchall.core.data.HomeRowQuery
import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.SeasonDetails
import com.watchall.core.data.SeasonSummary
import com.watchall.core.data.TitleRanking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class TmdbConfigurationException(message: String) : IllegalStateException(message)

private data class MediaPage(
    val items: List<MediaSummary>,
    val page: Int,
    val totalPages: Int,
)

class TmdbClient(
    private val apiKey: String = BuildConfig.TMDB_API_KEY,
) {
    private val gson = Gson()
    private val baseUrl = "https://api.themoviedb.org/3/".toHttpUrl()

    private data class CacheEntry(val value: Any, val storedAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun requireKey() {
        if (apiKey.isBlank()) {
            throw TmdbConfigurationException(
                "The TMDB API key is missing. Add TMDB_API_KEY to native/local.properties and rebuild.",
            )
        }
    }

    private suspend fun <T> cached(
        key: String,
        ttlMs: Long,
        loader: suspend () -> T,
    ): T {
        val hit = cache[key]
        if (hit != null && System.currentTimeMillis() - hit.storedAt < ttlMs) {
            @Suppress("UNCHECKED_CAST")
            return hit.value as T
        }
        val value = loader()
        cache[key] = CacheEntry(value as Any, System.currentTimeMillis())
        if (cache.size > 120) {
            val oldest = cache.entries.minByOrNull { it.value.storedAt }?.key
            if (oldest != null) cache.remove(oldest)
        }
        return value
    }

    private fun apiUrl(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): HttpUrl {
        val builder = baseUrl.newBuilder().addPathSegments(path.trimStart('/'))
        builder.addQueryParameter("api_key", apiKey)
        params.forEach { (key, value) ->
            if (value.isNotBlank()) builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    /**
     * Every TMDB call does a blocking HTTP request plus Gson parsing, so it is
     * moved off the caller's dispatcher. Callers are almost always the UI
     * scope, and parsing a page of JSON on the main thread is one of the
     * biggest causes of dropped frames on low-end TV hardware.
     */
    private suspend fun getPage(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        defaultType: MediaType,
        ttlMs: Long = 2 * 60_000L,
    ): MediaPage = withContext(Dispatchers.IO) {
        requireKey()
        val url = apiUrl(path, params + ("language" to "en-US"))
        val dto = cached("page:${url}", ttlMs) {
            try {
                gson.fromJson(tmdbHttpClient.getText(url), TmdbPageDto::class.java)
            } catch (error: JsonSyntaxException) {
                throw IllegalStateException("TMDB returned an unreadable response.", error)
            }
        }
        MediaPage(
            items = dto.results
                .filterNot { it.adult }
                .mapNotNull { it.toSummaryOrNull(defaultType) },
            page = dto.page,
            totalPages = dto.totalPages,
        )
    }

    suspend fun featured(): List<MediaSummary> {
        val today = LocalDate.now()
        return getPage(
            "discover/movie",
            listOf(
                "region" to "IN",
                "with_release_type" to "2|3|4",
                "with_original_language" to "hi|en|pa|te|ta|ml|kn",
                "release_date.gte" to today.minusDays(45).toString(),
                "release_date.lte" to today.toString(),
                "sort_by" to "popularity.desc",
                "include_adult" to "false",
            ),
            MediaType.MOVIE,
        ).items
    }

    suspend fun nowPlaying(): List<MediaSummary> {
        val today = LocalDate.now()
        return getPage(
            "discover/movie",
            listOf(
                "region" to "IN",
                "sort_by" to "popularity.desc",
                "with_release_type" to "3",
                "with_original_language" to "hi",
                "release_date.gte" to today.minusDays(45).toString(),
                "release_date.lte" to today.toString(),
            ),
            MediaType.MOVIE,
        ).items
    }

    suspend fun hindiMovies(): List<MediaSummary> {
        val today = LocalDate.now()
        return getPage(
            "discover/movie",
            listOf(
                "region" to "IN",
                "page" to "1",
                "with_release_type" to "2|3|4",
                "with_original_language" to "hi",
                "release_date.gte" to today.minusDays(45).toString(),
                "release_date.lte" to today.toString(),
                "sort_by" to "popularity.desc",
            ),
            MediaType.MOVIE,
        ).items
    }

    suspend fun englishMovies(): List<MediaSummary> {
        val today = LocalDate.now()
        return getPage(
            "discover/movie",
            listOf(
                "region" to "IN",
                "page" to "1",
                "with_release_type" to "2|3|4",
                "with_original_language" to "en",
                "release_date.gte" to today.minusDays(45).toString(),
                "release_date.lte" to today.toString(),
                "sort_by" to "popularity.desc",
            ),
            MediaType.MOVIE,
        ).items
    }

    suspend fun punjabiMovies(): List<MediaSummary> {
        val today = LocalDate.now()
        return getPage(
            "discover/movie",
            listOf(
                "region" to "IN",
                "page" to "1",
                "with_original_language" to "pa",
                "release_date.lte" to today.toString(),
                "sort_by" to "release_date.desc",
            ),
            MediaType.MOVIE,
        ).items
    }

    suspend fun trendingTv(): List<MediaSummary> =
        getPage(
            "trending/tv/week",
            listOf("language" to "en-US"),
            MediaType.TV,
        ).items

    suspend fun rowPage(
        query: HomeRowQuery,
        page: Int,
        genreId: Int? = null,
        year: Int? = null,
    ): Pair<List<MediaSummary>, Int> {
        val today = LocalDate.now()
        val params = mutableListOf(
            "page" to page.toString(),
            "include_adult" to "false",
        )
        if (query.languages.isNotEmpty()) {
            params += "with_original_language" to query.languages.joinToString("|")
        }
        val path = query.path
        if (path == null) {
            params += "sort_by" to query.sortBy
            if (query.region != null && query.mediaType == MediaType.MOVIE) {
                params += "region" to query.region
            }
            // A specific release year replaces the relative "last N days"
            // window, otherwise an old year would always return nothing.
            when (query.mediaType) {
                MediaType.MOVIE -> {
                    if (!query.releaseTypes.isNullOrBlank()) {
                        params += "with_release_type" to query.releaseTypes
                    }
                    if (year != null) {
                        params += "primary_release_year" to year.toString()
                    } else {
                        if (query.releaseWindowDays != null) {
                            params += "release_date.gte" to today
                                .minusDays(query.releaseWindowDays.toLong())
                                .toString()
                        }
                        params += "release_date.lte" to today.toString()
                    }
                }

                MediaType.TV -> {
                    params += "include_null_first_air_dates" to "false"
                    if (year != null) {
                        params += "first_air_date_year" to year.toString()
                    } else {
                        if (query.releaseWindowDays != null) {
                            params += "first_air_date.gte" to today
                                .minusDays(query.releaseWindowDays.toLong())
                                .toString()
                        }
                        params += "first_air_date.lte" to today.toString()
                    }
                }
            }
            if (genreId != null) {
                params += "with_genres" to genreId.toString()
            }
            if (query.minVoteCount > 0) {
                params += "vote_count.gte" to query.minVoteCount.toString()
            }
            if (query.minRating > 0.0) {
                params += "vote_average.gte" to query.minRating.toString()
            }
        }
        val dto = getPage(
            path ?: "discover/${query.mediaType.apiValue}",
            params,
            query.mediaType,
        )
        return dto.items to dto.totalPages
    }

    suspend fun genres(type: MediaType): List<Genre> = withContext(Dispatchers.IO) {
        requireKey()
        val url = apiUrl("genre/${type.apiValue}/list", listOf("language" to "en"))
        cached("genres:${type.apiValue}", 24 * 60 * 60_000L) {
            gson.fromJson(tmdbHttpClient.getText(url), TmdbGenresDto::class.java)
                .genres.map { Genre(it.id, it.name) }
        }
    }

    suspend fun catalog(
        type: MediaType,
        language: String?,
        genreId: Int?,
        year: Int?,
        page: Int,
        ttlMs: Long = 2 * 60_000L,
    ): Pair<List<MediaSummary>, Int> {
        val today = LocalDate.now()
        val params = mutableListOf(
            "page" to page.toString(),
            "region" to "IN",
            "include_adult" to "false",
            "sort_by" to "popularity.desc",
        )
        if (language != null) params += "with_original_language" to language
        if (genreId != null) params += "with_genres" to genreId.toString()
        when (type) {
            MediaType.MOVIE -> {
                params += "with_release_type" to "2|3|4"
                params += "primary_release_date.lte" to today.toString()
                if (language == null) {
                    params += "with_original_language" to "hi|en|pa|te|ta|ml|kn"
                }
                if (year != null) params += "primary_release_year" to year.toString()
            }
            MediaType.TV -> {
                params += "include_null_first_air_dates" to "false"
                if (language == null) {
                    params += "with_original_language" to "hi|pa|te|ta|ml|kn"
                }
                if (year != null) params += "first_air_date_year" to year.toString()
            }
        }
        val dto = getPage("discover/${type.apiValue}", params, type, ttlMs)
        return dto.items to dto.totalPages
    }

    suspend fun search(query: String): List<MediaSummary> {
        val value = query.trim()
        if (value.isBlank()) return emptyList()
        requireKey()

        suspend fun safePage(path: String, defaultType: MediaType): MediaPage? =
            try {
                getPage(
                    path,
                    listOf("query" to value, "include_adult" to "false", "page" to "1"),
                    defaultType,
                    ttlMs = 60_000L,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }

        val pages = coroutineScope {
            val multi = async { safePage("search/multi", MediaType.MOVIE) }
            val movie = async { safePage("search/movie", MediaType.MOVIE) }
            val tv = async { safePage("search/tv", MediaType.TV) }
            listOf(multi.await(), movie.await(), tv.await())
        }

        if (pages.all { it == null }) {
            throw IllegalStateException("Search is unavailable right now. Check your connection and try again.")
        }

        val merged = LinkedHashMap<String, MediaSummary>()
        fun add(item: MediaSummary?) {
            if (item == null || item.id <= 0) return
            merged.putIfAbsent(item.key, item)
        }

        pages.forEach { page ->
            page?.items?.forEach { add(it) }
        }
        return TitleRanking.rank(merged.values.toList(), value).take(40)
    }

    suspend fun details(id: Int, type: MediaType): MediaDetails = withContext(Dispatchers.IO) {
        requireKey()
        val url = apiUrl(
            "${type.apiValue}/$id",
            listOf(
                "language" to "en-US",
                "append_to_response" to "credits,videos,release_dates,content_ratings,recommendations",
                "include_image_language" to "en,null",
            ),
        )
        val dto = cached("details:${type.apiValue}:$id", 5 * 60_000L) {
            gson.fromJson(tmdbHttpClient.getText(url), TmdbDetailsDto::class.java)
        }
        dto.toDomain(type)
    }

    /**
     * Builds the "More like this" row for an already-loaded title.
     *
     * This used to be awaited inside [details], which meant the details screen
     * waited for up to four extra discover calls before painting anything. It
     * is now a separate call so the screen can render immediately and fill the
     * row in a moment later. Until then the row shows TMDB's own picks, which
     * arrive with the details response.
     */
    suspend fun related(details: MediaDetails): List<MediaSummary> =
        withContext(Dispatchers.IO) { relatedTitles(details) }

    /**
     * TMDB's built-in recommendation list is overwhelmingly English for most
     * titles. Build a "More like this" row that starts with titles in the same
     * language as the title being viewed, then mixes in the TMDB picks and a
     * broad Hindi/English/regional-language selection from the same genre.
     */
    private suspend fun relatedTitles(details: MediaDetails): List<MediaSummary> {
        val primaryGenre = details.genres.firstOrNull()?.id
        val primaryLanguage = details.originalLanguage.trim().takeIf { it.isNotBlank() }
        val today = LocalDate.now()

        fun discoverParams(language: String): List<Pair<String, String>> {
            val params = mutableListOf(
                "sort_by" to "popularity.desc",
                "include_adult" to "false",
                "vote_count.gte" to "10",
                "with_original_language" to language,
            )
            if (primaryGenre != null) {
                params += "with_genres" to primaryGenre.toString()
            }
            when (details.mediaType) {
                MediaType.MOVIE -> {
                    params += "with_release_type" to "2|3|4"
                    params += "primary_release_date.lte" to today.toString()
                }

                MediaType.TV -> {
                    params += "include_null_first_air_dates" to "false"
                    params += "first_air_date.lte" to today.toString()
                }
            }
            return params
        }

        suspend fun fetch(language: String): List<MediaSummary> =
            try {
                getPage(
                    path = "discover/${details.mediaType.apiValue}",
                    params = discoverParams(language),
                    defaultType = details.mediaType,
                    ttlMs = 5 * 60_000L,
                ).items
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }

        val sameLanguage = primaryLanguage?.let { fetch(it) }.orEmpty()
        val regionalLanguages = listOf(
            "pa", "te", "ta", "ml", "kn", "mr", "bn", "gu", "ur", "or", "as", "ne", "si",
        ).filterNot { it.equals(primaryLanguage, true) }
        val languageBuckets = buildList {
            if (!primaryLanguage.equals("hi", true)) add("hi")
            if (!primaryLanguage.equals("en", true)) add("en")
            if (regionalLanguages.isNotEmpty()) add(regionalLanguages.joinToString("|"))
        }.distinct()
        val mixedBuckets = coroutineScope {
            languageBuckets.map { language -> async { fetch(language) } }.map { it.await() }
        }

        val merged = LinkedHashMap<String, MediaSummary>()
        fun tryAdd(item: MediaSummary): Boolean {
            if (item.id <= 0 || item.key == details.mediaKey) return false
            if (item.posterPath.isNullOrBlank() || merged.containsKey(item.key)) return false
            merged[item.key] = item
            return true
        }

        fun add(items: List<MediaSummary>, limit: Int) {
            var added = 0
            for (item in items) {
                if (added >= limit || merged.size >= 18) break
                if (tryAdd(item)) added += 1
            }
        }

        fun addInterleaved(buckets: List<List<MediaSummary>>, limit: Int) {
            var added = 0
            var index = 0
            while (added < limit && merged.size < 18) {
                var found = false
                for (bucket in buckets) {
                    val item = bucket.getOrNull(index) ?: continue
                    found = true
                    if (tryAdd(item)) {
                        added += 1
                        if (added >= limit || merged.size >= 18) break
                    }
                }
                if (!found) break
                index += 1
            }
        }

        // Same language first, then TMDB's own picks, then Hindi/English/regional
        // buckets interleaved one-by-one so the row is never English-only.
        add(sameLanguage, 8)
        add(details.recommendations, 4)
        addInterleaved(mixedBuckets, 18)
        return merged.values.take(18)
    }

    /**
     * Everything a person has appeared in, across films and series.
     *
     * TMDB's combined-credits response mixes both media types and repeats titles
     * a person worked on more than once, so the result is de-duplicated and kept
     * in popularity order for the filmography grid.
     */
    suspend fun personCredits(personId: Int): List<MediaSummary> = withContext(Dispatchers.IO) {
        requireKey()
        val url = apiUrl(
            "person/$personId/combined_credits",
            listOf("language" to "en-US"),
        )
        val dto = cached("person-credits:$personId", 10 * 60_000L) {
            gson.fromJson(
                tmdbHttpClient.getText(url),
                TmdbCombinedCreditsDto::class.java,
            )
        }
        dto.cast
            .asSequence()
            .filterNot { it.adult }
            .filter { MediaType.fromApi(it.mediaType) != null }
            .sortedByDescending { it.popularity }
            .mapNotNull { it.toSummaryOrNull(MediaType.MOVIE) }
            .distinctBy { it.key }
            .toList()
    }

    suspend fun season(id: Int, seasonNumber: Int): SeasonDetails = withContext(Dispatchers.IO) {
        requireKey()
        val url = apiUrl("tv/$id/season/$seasonNumber", listOf("language" to "en-US"))
        val dto = cached("season:$id:$seasonNumber", 10 * 60_000L) {
            gson.fromJson(tmdbHttpClient.getText(url), TmdbSeasonDetailsDto::class.java)
        }
        SeasonDetails(
            seasonNumber = dto.seasonNumber,
            name = dto.name.orEmpty().ifBlank { "Season $seasonNumber" },
            overview = dto.overview.orEmpty(),
            episodes = dto.episodes
                .sortedBy { it.episodeNumber }
                .map {
                    EpisodeSummary(
                        seasonNumber = it.seasonNumber,
                        episodeNumber = it.episodeNumber,
                        name = it.name.orEmpty().ifBlank { "Episode ${it.episodeNumber}" },
                        overview = it.overview.orEmpty(),
                        stillPath = it.stillPath,
                        runtimeMinutes = it.runtime ?: 0,
                        airDate = it.airDate.orEmpty(),
                        rating = it.voteAverage,
                    )
                },
        )
    }

    private fun TmdbMediaDto.toSummaryOrNull(defaultType: MediaType): MediaSummary? {
        if (adult || id <= 0) return null
        val resolvedType = MediaType.fromApi(mediaType) ?: defaultType
        if (mediaType == "person") return null
        val title = title ?: name ?: originalTitle ?: originalName ?: return null
        val date = releaseDate ?: firstAirDate
        return MediaSummary(
            id = id,
            title = title,
            overview = overview.orEmpty(),
            posterPath = posterPath,
            backdropPath = backdropPath,
            rating = voteAverage,
            year = date.orEmpty().take(4),
            mediaType = resolvedType,
            originalLanguage = originalLanguage.orEmpty(),
            genreIds = genreIds,
        )
    }

    private fun TmdbDetailsDto.toDomain(type: MediaType): MediaDetails {
        val date = releaseDate ?: firstAirDate
        val title = title ?: name ?: originalTitle ?: originalName ?: "Untitled"
        val seasons = seasons
            .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            .sortedBy { it.seasonNumber }
            .map {
                SeasonSummary(
                    seasonNumber = it.seasonNumber,
                    name = it.name.orEmpty().ifBlank { "Season ${it.seasonNumber}" },
                    overview = it.overview.orEmpty(),
                    episodeCount = it.episodeCount,
                    posterPath = it.posterPath,
                    airDate = it.airDate.orEmpty(),
                )
            }
        val cast = credits?.cast
            .orEmpty()
            .sortedBy { it.order }
            .take(24)
            .map { CastMember(it.id, it.name, it.character, it.profilePath) }
        val trailer = videos?.results
            .orEmpty()
            .filter { it.site.equals("YouTube", ignoreCase = true) }
            .sortedWith(
                compareByDescending<TmdbVideoDto> { it.official }
                    .thenBy { if (it.type.equals("Trailer", true)) 0 else if (it.type.equals("Teaser", true)) 1 else 2 }
                    .thenBy { if (it.language == "en") 0 else 1 },
            )
            .firstOrNull()
        val certification = certificationFor(type)
        val recommendations = recommendations?.results
            .orEmpty()
            .filterNot { it.adult }
            .mapNotNull { it.toSummaryOrNull(type) }
            .filter { it.id != id }
            .distinctBy { it.key }
            .take(18)
        return MediaDetails(
            id = id,
            title = title,
            overview = overview.orEmpty(),
            tagline = tagline.orEmpty(),
            mediaType = type,
            posterPath = posterPath,
            backdropPath = backdropPath,
            rating = voteAverage,
            voteCount = voteCount,
            year = date.orEmpty().take(4),
            releaseDate = date.orEmpty(),
            runtimeMinutes = if (type == MediaType.MOVIE) runtime ?: 0 else episodeRunTime?.firstOrNull() ?: 0,
            genres = genres.map { Genre(it.id, it.name) },
            originalLanguage = originalLanguage.orEmpty(),
            status = status.orEmpty(),
            numberOfSeasons = numberOfSeasons,
            numberOfEpisodes = numberOfEpisodes,
            seasons = seasons,
            cast = cast,
            trailerKey = trailer?.key?.takeIf { it.isNotBlank() },
            certification = certification,
            recommendations = recommendations,
        )
    }

    private fun TmdbDetailsDto.certificationFor(type: MediaType): String {
        if (type == MediaType.MOVIE) {
            val country = releaseDates?.results?.firstOrNull { it.country == "IN" }
                ?: releaseDates?.results?.firstOrNull { it.country == "US" }
            return country?.releaseDates
                ?.firstOrNull { !it.certification.isNullOrBlank() }
                ?.certification
                .orEmpty()
        }
        val rating = contentRatings?.results
            ?.firstOrNull { it.country == "IN" }
            ?: contentRatings?.results?.firstOrNull { it.country == "US" }
        return rating?.rating.orEmpty()
    }
}
