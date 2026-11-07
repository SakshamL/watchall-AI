package com.watchall.core.util

import com.watchall.core.data.SourceList
import com.watchall.core.net.ExtractionException
import com.watchall.core.net.HttpStatusException
import com.watchall.core.net.TmdbConfigurationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object UiText {
    fun sourceName(name: String?): String {
        if (name.isNullOrBlank()) return "Auto"
        return when (name.lowercase()) {
            "vidnest" -> SourceList.SERVER_TWO.displayName
            "vegamovies" -> SourceList.SERVER_THREE.displayName
            "viduki" -> SourceList.SERVER_ONE.displayName
            else -> name
        }
    }

    fun sanitize(message: String): String =
        message
            .replace("Vidnest", SourceList.SERVER_TWO.displayName, ignoreCase = true)
            .replace("Vegamovies", SourceList.SERVER_THREE.displayName, ignoreCase = true)
            .replace("Viduki", SourceList.SERVER_ONE.displayName, ignoreCase = true)

    fun friendlyError(error: Throwable): String {
        val message = when (error) {
            is TmdbConfigurationException -> error.message ?: "The TMDB API key is missing."
            is ExtractionException -> error.message
            is HttpStatusException -> when (error.statusCode) {
                401, 403 -> "The TMDB API key is missing or invalid."
                404 -> "That title could not be found."
                429 -> "Too many requests right now. Please wait a moment and try again."
                else -> "The service returned an error (${error.statusCode}). Please try again."
            }
            is UnknownHostException -> "No connection to the server. Check your network and server address."
            is SocketTimeoutException -> "The server took too long to respond. Please try again."
            is IOException -> "The connection was interrupted. Check your network and try again."
            else -> error.message ?: "Something went wrong. Please try again."
        }
        return sanitize(message)
    }

    fun connectionLabel(url: String): String = url.removePrefix("http://").removePrefix("https://")
}
