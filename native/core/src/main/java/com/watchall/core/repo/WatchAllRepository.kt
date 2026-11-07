package com.watchall.core.repo

import com.watchall.core.data.AppSettings
import com.watchall.core.data.HomeFeed
import com.watchall.core.data.HomeRow
import com.watchall.core.data.HomeRowQuery
import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.SeasonDetails
import com.watchall.core.data.ServerInfo
import com.watchall.core.data.SourceList
import com.watchall.core.data.StreamResult
import com.watchall.core.data.WatchedEntry
import com.watchall.core.data.WatchlistItem
import com.watchall.core.net.TmdbClient
import com.watchall.core.net.WatchAllApi
import com.watchall.core.prefs.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow

class WatchAllRepository(
    private val tmdb: TmdbClient,
    private val api: WatchAllApi,
    private val preferences: PreferencesStore,
) {
    val settings: StateFlow<AppSettings> = preferences.settings

    suspend fun home(): HomeFeed = coroutineScope {
        val featured = async { safe { tmdb.featured() } }
        val rowSpecs = homeRowSpecs()
        val rowItems = rowSpecs.map { spec ->
            async {
                safe {
                    if (spec.groups.isEmpty()) {
                        tmdb.rowPage(spec.query, 1).first
                    } else {
                        groupedRow(spec.groups)
                    }
                }.orEmpty()
            }
        }

        val featuredItems = featured.await().orEmpty()
        val rows = rowSpecs.mapIndexedNotNull { index, spec ->
            val items = rowItems[index].await()
                .distinctBy { it.key }
                .let { if (spec.limit != null) it.take(spec.limit) else it }
            if (items.isEmpty()) {
                null
            } else {
                HomeRow(
                    id = spec.id,
                    title = spec.title,
                    eyebrow = spec.eyebrow,
                    items = items,
                    query = spec.query,
                    ranked = spec.ranked,
                )
            }
        }
        val hero = featuredItems.ifEmpty { rows.firstOrNull()?.items.orEmpty() }
            .distinctBy { it.key }
            .take(6)
        if (hero.isEmpty() && rows.isEmpty()) {
            throw IllegalStateException(
                "WatchAll could not load the catalogue. Check your connection and TMDB configuration.",
            )
        }
        HomeFeed(featured = hero, rows = rows)
    }

    suspend fun collection(
        row: HomeRow,
        page: Int,
        genreId: Int? = null,
        year: Int? = null,
    ): Pair<List<MediaSummary>, Int> {
        val query = row.query
            ?: throw IllegalStateException("This collection cannot be opened yet.")
        return tmdb.rowPage(query, page, genreId, year)
    }

    suspend fun catalog(
        type: MediaType,
        language: String?,
        genreId: Int?,
        year: Int?,
        page: Int,
    ): Pair<List<MediaSummary>, Int> =
        tmdb.catalog(type, language, genreId, year, page)

    suspend fun genres(type: MediaType) = tmdb.genres(type)

    suspend fun search(query: String): List<MediaSummary> =
        tmdb.search(query).also { preferences.rememberSearch(query) }

    fun recentSearches(): List<String> = preferences.recentSearches()

    fun removeRecentSearch(query: String) = preferences.removeRecentSearch(query)

    fun clearRecentSearches() = preferences.clearRecentSearches()

    suspend fun details(id: Int, type: MediaType): MediaDetails = tmdb.details(id, type)

    suspend fun personCredits(personId: Int): List<MediaSummary> {
        if (personId <= 0) return emptyList()
        return tmdb.personCredits(personId)
    }

    suspend fun season(id: Int, seasonNumber: Int): SeasonDetails = tmdb.season(id, seasonNumber)

    suspend fun servers(): List<ServerInfo> = api.servers()

    suspend fun extract(
        mediaId: Int,
        mediaType: MediaType,
        source: SourceList,
        language: String,
        server: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): StreamResult = api.extract(
        mediaId = mediaId,
        mediaType = mediaType,
        source = source,
        language = language,
        server = server,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )

    fun resume(mediaKey: String): ResumeEntry? = preferences.resume(mediaKey)

    fun continueWatching(): List<ResumeEntry> = preferences.continueWatching()

    fun saveProgress(entry: ResumeEntry) = preferences.saveResume(entry)

    fun clearProgress(mediaKey: String) = preferences.removeResume(mediaKey)

    fun watched(): List<WatchedEntry> = preferences.watched()

    fun markWatched(entry: ResumeEntry) = preferences.markWatched(entry)

    fun removeWatched(mediaKey: String) = preferences.removeWatched(mediaKey)

    fun watchlist(): List<WatchlistItem> = preferences.watchlist()

    fun isWatchlisted(media: MediaSummary): Boolean = preferences.isWatchlisted(media)

    fun toggleWatchlist(media: MediaSummary): Boolean = preferences.toggleWatchlist(media)

    fun currentSettings(): AppSettings = preferences.currentSettings()

    fun updateApiBaseUrl(raw: String): AppSettings = preferences.updateApiBaseUrl(raw)

    fun resetApiBaseUrl(): AppSettings = preferences.resetApiBaseUrl()

    fun normalizeApiBaseUrl(raw: String): String = preferences.normalizeBaseUrl(raw)

    suspend fun testConnection(): List<ServerInfo> = api.servers()

    private suspend fun groupedRow(groups: List<HomeRowGroup>): List<MediaSummary> =
        coroutineScope {
            val pages = groups.map { group ->
                async { safe { tmdb.rowPage(group.query, 1).first }.orEmpty() }
            }
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<MediaSummary>()
            groups.forEachIndexed { index, group ->
                var taken = 0
                for (item in pages[index].await()) {
                    if (taken >= group.limit) break
                    if (item.posterPath.isNullOrBlank()) continue
                    if (!seen.add(item.key)) continue
                    merged += item
                    taken += 1
                }
            }
            merged
        }

    private fun homeRowSpecs(): List<HomeRowSpec> {
        val indianMovies = listOf("hi", "en", "pa", "te", "ta", "ml", "kn")
        // Now Playing is a Delhi-first mix: Hindi, then English, Punjabi and the
        // rest of India. Each group keeps its own popularity order.
        val nowPlayingWindow = HomeRowQuery(
            mediaType = MediaType.MOVIE,
            releaseTypes = "3",
            releaseWindowDays = 90,
            minVoteCount = 1,
        )
        fun nowPlayingGroup(languages: List<String>) =
            nowPlayingWindow.copy(languages = languages)
        return listOf(
            HomeRowSpec(
                id = "top-10",
                title = "Top 10 Today",
                eyebrow = "The whole country is watching",
                query = HomeRowQuery(
                    mediaType = MediaType.MOVIE,
                    languages = indianMovies,
                    releaseTypes = "2|3|4",
                    releaseWindowDays = 90,
                    sortBy = "popularity.desc",
                    minVoteCount = 20,
                ),
                ranked = true,
                limit = 10,
            ),
            HomeRowSpec(
                id = "now-playing",
                title = "Now Playing",
                eyebrow = "Hindi first · then English · Punjabi · pan-India",
                query = nowPlayingGroup(
                    listOf(
                        "hi", "en", "pa", "te", "ta", "ml", "kn",
                        "mr", "bn", "gu", "ur", "or", "as", "ne", "si",
                    ),
                ),
                limit = 18,
                groups = listOf(
                    HomeRowGroup(nowPlayingGroup(listOf("hi")), 7),
                    HomeRowGroup(nowPlayingGroup(listOf("en")), 4),
                    HomeRowGroup(nowPlayingGroup(listOf("pa")), 3),
                    HomeRowGroup(
                        nowPlayingGroup(listOf("te", "ta", "ml", "kn", "mr", "bn", "gu", "ur", "or", "as", "ne", "si")),
                        4,
                    ),
                ),
            ),
            HomeRowSpec(
                id = "hindi",
                title = "Blockbuster Hindi",
                eyebrow = "Big-screen favourites",
                query = HomeRowQuery(
                    mediaType = MediaType.MOVIE,
                    languages = listOf("hi"),
                    releaseTypes = "2|3|4",
                    releaseWindowDays = 180,
                    minVoteCount = 20,
                ),
            ),
            HomeRowSpec(
                id = "english",
                title = "English Picks",
                eyebrow = "Critically loved",
                query = HomeRowQuery(
                    mediaType = MediaType.MOVIE,
                    languages = listOf("en"),
                    releaseTypes = "2|3|4",
                    releaseWindowDays = 180,
                    minVoteCount = 30,
                ),
            ),
            HomeRowSpec(
                id = "south",
                title = "South Indian Hits",
                eyebrow = "The stories everyone is talking about",
                query = HomeRowQuery(
                    mediaType = MediaType.MOVIE,
                    languages = listOf("te", "ta", "ml", "kn"),
                    releaseTypes = "2|3|4",
                    releaseWindowDays = 180,
                    minVoteCount = 15,
                ),
            ),
            HomeRowSpec(
                id = "punjabi",
                title = "Latest Punjabi",
                eyebrow = "Fresh releases",
                query = HomeRowQuery(
                    mediaType = MediaType.MOVIE,
                    languages = listOf("pa"),
                    releaseTypes = "2|3|4",
                    sortBy = "release_date.desc",
                ),
            ),
            HomeRowSpec(
                id = "acclaimed",
                title = "Critically Acclaimed",
                eyebrow = "The ones worth staying up for",
                query = HomeRowQuery(
                    mediaType = MediaType.MOVIE,
                    languages = indianMovies,
                    sortBy = "vote_average.desc",
                    minVoteCount = 500,
                    minRating = 7.5,
                ),
            ),
            HomeRowSpec(
                id = "tv",
                title = "Trending TV",
                eyebrow = "Binge-worthy series",
                query = HomeRowQuery(
                    mediaType = MediaType.TV,
                    path = "trending/tv/week",
                ),
            ),
            HomeRowSpec(
                id = "top-rated-series",
                title = "Top Rated Series",
                eyebrow = "New worlds, one episode at a time",
                query = HomeRowQuery(
                    mediaType = MediaType.TV,
                    sortBy = "vote_average.desc",
                    minVoteCount = 1000,
                    minRating = 8.0,
                ),
            ),
        )
    }

    private suspend fun <T> safe(block: suspend () -> T): T? =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}

private data class HomeRowSpec(
    val id: String,
    val title: String,
    val eyebrow: String,
    val query: HomeRowQuery,
    val ranked: Boolean = false,
    val limit: Int? = null,
    val groups: List<HomeRowGroup> = emptyList(),
)

private data class HomeRowGroup(
    val query: HomeRowQuery,
    val limit: Int,
)
