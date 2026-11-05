package com.watchall.core.data

import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {
    fun runtime(minutes: Int): String {
        if (minutes <= 0) return ""
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun time(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun compactNumber(value: Int): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }

    fun rating(value: Double): String =
        if (value > 0) String.format(Locale.US, "%.1f", value) else "—"

    fun languageName(code: String): String = when (code.lowercase()) {
        "en" -> "English"
        "hi" -> "Hindi"
        "pa" -> "Punjabi"
        "te" -> "Telugu"
        "ta" -> "Tamil"
        "ml" -> "Malayalam"
        "kn" -> "Kannada"
        "ko" -> "Korean"
        "ja" -> "Japanese"
        "fr" -> "French"
        "es" -> "Spanish"
        "de" -> "German"
        else -> code.uppercase()
    }

    fun progressPercent(positionMs: Long, durationMs: Long): Int =
        if (durationMs > 0) ((positionMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 100) else 0
}
