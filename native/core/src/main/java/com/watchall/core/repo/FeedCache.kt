package com.watchall.core.repo

import android.content.Context
import com.google.gson.Gson
import com.watchall.core.data.HomeFeed
import java.io.File

/**
 * Last successfully loaded home feed, kept on disk.
 *
 * Building the home screen used to require about ten TMDB requests, so a cold
 * start showed a skeleton until the slowest one finished. The feed changes
 * slowly (rows are popularity lists), so the previous copy is painted straight
 * away and the network refreshes it behind the user's back.
 */
class FeedCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, "home_feed.json")
    private val gson = Gson()
    private val lock = Any()

    data class Snapshot(val feed: HomeFeed, val savedAt: Long) {
        fun ageMs(now: Long = System.currentTimeMillis()): Long = (now - savedAt).coerceAtLeast(0L)
    }

    fun read(): Snapshot? = synchronized(lock) {
        if (!file.exists()) return null
        runCatching {
            val raw = file.readText()
            if (raw.isBlank()) return null
            gson.fromJson(raw, Snapshot::class.java)
                ?.takeIf { it.feed.rows.isNotEmpty() || it.feed.featured.isNotEmpty() }
        }.getOrNull()
    }

    fun write(feed: HomeFeed) {
        synchronized(lock) {
            runCatching {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(gson.toJson(Snapshot(feed, System.currentTimeMillis())))
                // Rename is atomic on the same filesystem, so a crash mid-write
                // cannot leave a half-written feed behind.
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file.delete() }
        }
    }
}
