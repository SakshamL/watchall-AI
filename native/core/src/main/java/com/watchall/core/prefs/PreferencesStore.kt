package com.watchall.core.prefs

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.watchall.core.BuildConfig
import com.watchall.core.data.AppSettings
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.WatchedEntry
import com.watchall.core.data.WatchlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.lang.reflect.Type
import java.util.concurrent.Executors

/**
 * Small on-device store for resume points, watched history, the watchlist and
 * recent searches.
 *
 * Everything is kept in memory after it is first read, and all JSON parsing and
 * disk writes happen on a single background thread. Previously every read
 * parsed the whole stored list and every write re-parsed and re-serialised it
 * on whichever thread called it — which is the main thread whenever the UI asks
 * "what was I watching?" or the player autosaves a position every few seconds.
 * On a 1 GB TV stick that was enough to stall frames.
 */
class PreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchall_native", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    /** Serialises all background persistence so writes never overtake each other. */
    private val disk = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "watchall-prefs").apply { isDaemon = true }
    }

    private val resumeType: Type = object : TypeToken<List<ResumeEntry>>() {}.type
    private val watchedType: Type = object : TypeToken<List<WatchedEntry>>() {}.type
    private val watchlistType: Type = object : TypeToken<List<WatchlistItem>>() {}.type
    private val recentSearchType: Type = object : TypeToken<List<String>>() {}.type

    @Volatile private var resumeCache: List<ResumeEntry>? = null
    @Volatile private var watchedCache: List<WatchedEntry>? = null
    @Volatile private var watchlistCache: List<WatchlistItem>? = null
    @Volatile private var recentSearchCache: List<String>? = null

    private val defaultApi = BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        // Read and parse the stored lists once, in the background, while the
        // first screen is still being built.
        disk.execute {
            runCatching {
                continueWatching()
                watched()
                watchlist()
                recentSearches()
            }
        }
    }

    fun currentSettings(): AppSettings = _settings.value

    private fun readSettings(): AppSettings {
        val stored = prefs.getString(KEY_API_BASE_URL, null)
        val migrated = migrateLegacyServerAddress(stored)
        val api = normalizeBaseUrl(migrated ?: defaultApi)
        return AppSettings(apiBaseUrl = api, defaultApiBaseUrl = defaultApi)
    }

    /**
     * Early sideload builds shipped with a LAN/emulator address compiled in as
     * the "default". Anyone who installed one of those builds keeps that address
     * in SharedPreferences, so a new build with the WatchAll server default
     * would still point at a machine on the local network.
     *
     * This migration deliberately runs every launch instead of using a one-shot
     * flag. An older build could have set that flag while the compiled default
     * was still a LAN address, which would leave an upgraded install pointing at
     * a dead development server forever. These exact addresses are only ever
     * development defaults, never a real user choice, so removing them is safe.
     */
    private fun migrateLegacyServerAddress(stored: String?): String? {
        val normalized = stored?.trim()?.trimEnd('/')
        if (normalized.isNullOrBlank()) return null
        if (normalized !in LEGACY_SERVER_ADDRESSES) return stored
        if (defaultApi in LEGACY_SERVER_ADDRESSES) return stored
        prefs.edit().remove(KEY_API_BASE_URL).apply()
        return null
    }

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return defaultApi
        val withScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            // A bare hostname is almost always a public server. Keep plain HTTP
            // only for addresses that can physically be on the local network.
            isLocalAddress(trimmed) -> "http://$trimmed"
            else -> "https://$trimmed"
        }
        return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: withScheme
    }

    private fun isLocalAddress(value: String): Boolean {
        val host = value.substringBefore('/').substringBefore(':').lowercase()
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "10.0.2.2" ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.startsWith("172.16.") ||
            host.startsWith("172.17.") ||
            host.startsWith("172.18.") ||
            host.startsWith("172.19.") ||
            host.startsWith("172.2") ||
            host.startsWith("172.30.") ||
            host.startsWith("172.31.") ||
            host.endsWith(".local")
    }

    fun updateApiBaseUrl(raw: String): AppSettings {
        val normalized = normalizeBaseUrl(raw)
        persistString(KEY_API_BASE_URL, normalized)
        val next = AppSettings(normalized, defaultApi)
        _settings.value = next
        return next
    }

    fun resetApiBaseUrl(): AppSettings {
        asyncPersist { prefs.edit().remove(KEY_API_BASE_URL).apply() }
        val next = AppSettings(defaultApi, defaultApi)
        _settings.value = next
        return next
    }

    fun saveResume(entry: ResumeEntry) {
        if (entry.positionMs < MIN_RESUME_MS) {
            removeResume(entry.mediaKey)
            return
        }
        if (entry.durationMs > 0 && entry.positionMs.toFloat() / entry.durationMs >= 0.96f) {
            removeResume(entry.mediaKey)
            return
        }
        synchronized(lock) {
            val entries = readResume()
                .filterNot { it.mediaKey == entry.mediaKey }
                .plus(entry.copy(updatedAt = System.currentTimeMillis()))
                .sortedByDescending { it.updatedAt }
                .take(MAX_RESUME_ENTRIES)
            resumeCache = entries
            persist(KEY_RESUME, entries, resumeType)
        }
    }

    fun removeResume(mediaKey: String) {
        synchronized(lock) {
            val entries = readResume().filterNot { it.mediaKey == mediaKey }
            resumeCache = entries
            persist(KEY_RESUME, entries, resumeType)
        }
    }

    fun resume(mediaKey: String): ResumeEntry? =
        readResume().firstOrNull { it.mediaKey == mediaKey }

    fun continueWatching(): List<ResumeEntry> = readResume()

    private fun readResume(): List<ResumeEntry> {
        resumeCache?.let { return it }
        synchronized(lock) {
            resumeCache?.let { return it }
            return load<ResumeEntry>(KEY_RESUME, resumeType).also { resumeCache = it }
        }
    }

    /**
     * Record a title as watched. Movies are stored once per title; TV shows
     * keep one entry per show plus the set of completed episodes, so replaying
     * an episode does not create duplicates.
     */
    fun markWatched(entry: ResumeEntry) {
        val titleKey = when (entry.mediaType) {
            MediaType.MOVIE -> entry.mediaKey
            MediaType.TV -> "tv-${entry.mediaId}"
        }
        if (titleKey.isBlank() || entry.mediaId <= 0) return
        synchronized(lock) {
            val current = readWatched()
            val existing = current.firstOrNull { it.mediaKey == titleKey }
            val episodeKey = if (entry.mediaType == MediaType.TV) {
                val season = entry.seasonNumber ?: 1
                val episode = entry.episodeNumber ?: 1
                "s${season}e${episode}"
            } else {
                null
            }
            val episodes = if (episodeKey == null) {
                existing?.episodes.orEmpty()
            } else {
                (existing?.episodes.orEmpty() + episodeKey).distinct()
            }
            val label = if (entry.mediaType == MediaType.TV) {
                entry.subtitle.ifBlank {
                    val season = entry.seasonNumber ?: 1
                    val episode = entry.episodeNumber ?: 1
                    "S${season.toString().padStart(2, '0')} E${episode.toString().padStart(2, '0')}"
                }
            } else {
                null
            }
            val merged = WatchedEntry(
                mediaKey = titleKey,
                mediaId = entry.mediaId,
                mediaType = entry.mediaType,
                title = entry.title,
                posterPath = entry.posterPath ?: existing?.posterPath,
                backdropPath = entry.backdropPath ?: existing?.backdropPath,
                originalLanguage = entry.originalLanguage.ifBlank { existing?.originalLanguage.orEmpty() },
                year = existing?.year.orEmpty(),
                lastEpisodeLabel = label ?: existing?.lastEpisodeLabel,
                episodes = episodes,
                watchedAt = System.currentTimeMillis(),
            )
            val next = current
                .filterNot { it.mediaKey == titleKey }
                .plus(merged)
                .sortedByDescending { it.watchedAt }
                .take(MAX_WATCHED_ENTRIES)
            watchedCache = next
            persist(KEY_WATCHED, next, watchedType)
        }
    }

    fun removeWatched(mediaKey: String) {
        synchronized(lock) {
            val next = readWatched().filterNot { it.mediaKey == mediaKey }
            watchedCache = next
            persist(KEY_WATCHED, next, watchedType)
        }
    }

    fun watched(): List<WatchedEntry> = readWatched()

    private fun readWatched(): List<WatchedEntry> {
        watchedCache?.let { return it }
        synchronized(lock) {
            watchedCache?.let { return it }
            return load<WatchedEntry>(KEY_WATCHED, watchedType).also { watchedCache = it }
        }
    }

    fun watchlist(): List<WatchlistItem> = readWatchlist()

    private fun readWatchlist(): List<WatchlistItem> {
        watchlistCache?.let { return it }
        synchronized(lock) {
            watchlistCache?.let { return it }
            return load<WatchlistItem>(KEY_WATCHLIST, watchlistType).also { watchlistCache = it }
        }
    }

    fun isWatchlisted(media: MediaSummary): Boolean =
        watchlist().any { it.media.key == media.key }

    fun toggleWatchlist(media: MediaSummary): Boolean {
        synchronized(lock) {
            val current = watchlist()
            val exists = current.any { it.media.key == media.key }
            val next = if (exists) {
                current.filterNot { it.media.key == media.key }
            } else {
                current + WatchlistItem(media)
            }
            watchlistCache = next
            persist(KEY_WATCHLIST, next, watchlistType)
            return !exists
        }
    }

    fun recentSearches(): List<String> = readRecentSearches()

    private fun readRecentSearches(): List<String> {
        recentSearchCache?.let { return it }
        synchronized(lock) {
            recentSearchCache?.let { return it }
            return load<String>(KEY_RECENT_SEARCHES, recentSearchType).also { recentSearchCache = it }
        }
    }

    fun rememberSearch(query: String) {
        val value = query.trim()
        if (value.length < 2) return
        synchronized(lock) {
            val next = (listOf(value) + recentSearches().filterNot { it.equals(value, true) })
                .take(8)
            recentSearchCache = next
            persist(KEY_RECENT_SEARCHES, next, recentSearchType)
        }
    }

    fun removeRecentSearch(query: String) {
        synchronized(lock) {
            val next = recentSearches().filterNot { it.equals(query.trim(), true) }
            recentSearchCache = next
            persist(KEY_RECENT_SEARCHES, next, recentSearchType)
        }
    }

    fun clearRecentSearches() {
        synchronized(lock) {
            recentSearchCache = emptyList()
            asyncPersist { prefs.edit().remove(KEY_RECENT_SEARCHES).apply() }
        }
    }

    /** Reads and parses a stored JSON list. Only ever called off the main thread after warm-up. */
    private fun <T> load(key: String, type: Type): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            gson.fromJson<List<T>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Serialise and write off the main thread; the in-memory copy is already current. */
    private fun <T> persist(key: String, value: List<T>, type: Type) {
        disk.execute {
            runCatching { prefs.edit().putString(key, gson.toJson(value, type)).apply() }
        }
    }

    private fun persistString(key: String, value: String) {
        asyncPersist { prefs.edit().putString(key, value).apply() }
    }

    private fun asyncPersist(block: () -> Unit) {
        disk.execute { runCatching(block) }
    }

    companion object {
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_RESUME = "resume_entries"
        private const val KEY_WATCHLIST = "watchlist"
        private const val KEY_WATCHED = "watched_entries"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MIN_RESUME_MS = 10_000L
        private const val MAX_RESUME_ENTRIES = 30
        private const val MAX_WATCHED_ENTRIES = 200
        private val LEGACY_SERVER_ADDRESSES = setOf(
            "http://192.168.1.4:3001",
            "http://10.0.2.2:3001",
            "http://localhost:3001",
            "http://127.0.0.1:3001",
        )
    }
}
