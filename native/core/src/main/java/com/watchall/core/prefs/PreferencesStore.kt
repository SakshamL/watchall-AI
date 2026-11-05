package com.watchall.core.prefs

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.watchall.core.BuildConfig
import com.watchall.core.data.AppSettings
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.WatchlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class PreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchall_native", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    private val defaultApi = BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun currentSettings(): AppSettings = _settings.value

    private fun readSettings(): AppSettings {
        val stored = prefs.getString(KEY_API_BASE_URL, null)
        val api = normalizeBaseUrl(stored ?: defaultApi)
        return AppSettings(apiBaseUrl = api, defaultApiBaseUrl = defaultApi)
    }

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return defaultApi
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: withScheme
    }

    fun updateApiBaseUrl(raw: String): AppSettings {
        val normalized = normalizeBaseUrl(raw)
        prefs.edit().putString(KEY_API_BASE_URL, normalized).apply()
        val next = AppSettings(normalized, defaultApi)
        _settings.value = next
        return next
    }

    fun resetApiBaseUrl(): AppSettings {
        prefs.edit().remove(KEY_API_BASE_URL).apply()
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
            prefs.edit().putString(KEY_RESUME, gson.toJson(entries)).apply()
        }
    }

    fun removeResume(mediaKey: String) {
        synchronized(lock) {
            val entries = readResume().filterNot { it.mediaKey == mediaKey }
            prefs.edit().putString(KEY_RESUME, gson.toJson(entries)).apply()
        }
    }

    fun resume(mediaKey: String): ResumeEntry? =
        readResume().firstOrNull { it.mediaKey == mediaKey }

    fun continueWatching(): List<ResumeEntry> = readResume()

    private fun readResume(): List<ResumeEntry> {
        val raw = prefs.getString(KEY_RESUME, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ResumeEntry>>() {}.type
            gson.fromJson<List<ResumeEntry>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun watchlist(): List<WatchlistItem> {
        val raw = prefs.getString(KEY_WATCHLIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchlistItem>>() {}.type
            gson.fromJson<List<WatchlistItem>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
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
            prefs.edit().putString(KEY_WATCHLIST, gson.toJson(next)).apply()
            return !exists
        }
    }

    fun recentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun rememberSearch(query: String) {
        val value = query.trim()
        if (value.length < 2) return
        synchronized(lock) {
            val next = (listOf(value) + recentSearches().filterNot { it.equals(value, true) })
                .take(8)
            prefs.edit().putString(KEY_RECENT_SEARCHES, gson.toJson(next)).apply()
        }
    }

    fun removeRecentSearch(query: String) {
        synchronized(lock) {
            val next = recentSearches().filterNot { it.equals(query.trim(), true) }
            prefs.edit().putString(KEY_RECENT_SEARCHES, gson.toJson(next)).apply()
        }
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    companion object {
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_RESUME = "resume_entries"
        private const val KEY_WATCHLIST = "watchlist"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MIN_RESUME_MS = 10_000L
        private const val MAX_RESUME_ENTRIES = 30
    }
}
