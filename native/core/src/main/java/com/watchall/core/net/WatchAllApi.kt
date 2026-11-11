package com.watchall.core.net

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.watchall.core.data.MediaType
import com.watchall.core.data.CaptionTrack
import com.watchall.core.data.ServerInfo
import com.watchall.core.data.SourceList
import com.watchall.core.data.StreamResult
import com.watchall.core.data.StreamVariant
import com.watchall.core.prefs.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Locale

class ExtractionException(
    override val message: String,
    val statusCode: Int = 0,
) : IOException(message)

private data class ExtractResponseDto(
    @SerializedName("streamUrl") val streamUrl: String? = null,
    val type: String? = null,
    val server: String? = null,
    val referer: String? = null,
    @SerializedName("forceProxy") val forceProxy: Boolean = false,
    val error: String? = null,
    val hls: String? = null,
    val dash: String? = null,
    val mp4: String? = null,
    val streams: List<StreamVariantDto> = emptyList(),
    val candidates: List<StreamCandidateDto> = emptyList(),
    val captionsUrl: String? = null,
    val captions: List<CaptionTrackDto> = emptyList(),
)

private data class StreamVariantDto(
    val url: String? = null,
    val type: String? = null,
    val quality: Int? = null,
    val height: Int? = null,
    val codec: String? = null,
    val verified: Boolean = false,
)

private data class StreamCandidateDto(
    val url: String? = null,
    val type: String? = null,
    val quality: Int? = null,
    val height: Int? = null,
    val codec: String? = null,
    val verified: Boolean = false,
)

private data class CaptionTrackDto(
    val url: String? = null,
    val src: String? = null,
    val subtitleUrl: String? = null,
    val captionUrl: String? = null,
    val fileUrl: String? = null,
    val label: String? = null,
    val name: String? = null,
    val title: String? = null,
    val language: String? = null,
    val lang: String? = null,
    val languageCode: String? = null,
    val locale: String? = null,
    val format: String? = null,
)

private data class ServersResponseDto(
    val servers: List<ServerDto> = emptyList(),
    val error: String? = null,
)

private data class ServerDto(
    val name: String = "",
    val label: String = "",
    val language: String = "",
    val mp4: Boolean = false,
    val upscale: Boolean = false,
)

private data class MediaInfoDto(
    @SerializedName("contentType") val contentType: String? = null,
)

class WatchAllApi(
    private val preferences: PreferencesStore,
) {
    private val gson = Gson()

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return preferences.currentSettings().defaultApiBaseUrl
        val withScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            isLocalAddress(trimmed) -> "http://$trimmed"
            else -> "https://$trimmed"
        }
        return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/')
            ?: withScheme.trimEnd('/')
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

    fun baseUrl(): String = normalizeBaseUrl(preferences.currentSettings().apiBaseUrl)

    private fun endpoint(path: String): HttpUrl =
        (baseUrl() + "/" + path.trimStart('/')).toHttpUrl()

    private fun resolveRelative(raw: String): String {
        val value = raw.trim()
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("//")) return "https:" + value
        if (value.startsWith("/")) return baseUrl() + value
        return baseUrl() + "/" + value
    }

    private fun proxyUrl(rawUrl: String, type: String, referer: String): String {
        if (rawUrl.contains("/api/proxy")) return rawUrl
        return endpoint("api/proxy").newBuilder()
            .addQueryParameter("url", rawUrl)
            .addQueryParameter("type", type)
            .apply {
                if (referer.isNotBlank()) addQueryParameter("referer", referer)
            }
            .build()
            .toString()
    }

    suspend fun servers(): List<ServerInfo> = withContext(Dispatchers.IO) {
        val url = endpoint("api/servers")
        val body = backendHttpClient.getText(url)
        val dto = gson.fromJson(body, ServersResponseDto::class.java)
        if (dto.error != null && dto.servers.isEmpty()) {
            throw ExtractionException(dto.error)
        }
        dto.servers
            .filter { it.name.isNotBlank() }
            .map {
                ServerInfo(
                    name = it.name,
                    label = it.label.ifBlank { it.name },
                    language = it.language,
                )
            }
    }

    suspend fun extract(
        mediaId: Int,
        mediaType: MediaType,
        source: SourceList,
        language: String = "",
        server: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): StreamResult = withContext(Dispatchers.IO) {
        if (mediaType == MediaType.TV && !source.supportsTv) {
            throw ExtractionException("Server List 2 is available for movies only.")
        }

        val builder = endpoint("api/extract").newBuilder()
            .addQueryParameter("tmdb", mediaId.toString())
            .addQueryParameter("type", mediaType.apiValue)
            .addQueryParameter("source", source.apiId)
        if (language.isNotBlank()) builder.addQueryParameter("lang", language)
        if (!server.isNullOrBlank() && source == SourceList.SERVER_ONE) {
            builder.addQueryParameter("srv", server.replace(Regex("[^A-Za-z]"), ""))
        }
        if (mediaType == MediaType.TV) {
            if (seasonNumber != null) builder.addQueryParameter("season", seasonNumber.toString())
            if (episodeNumber != null) builder.addQueryParameter("episode", episodeNumber.toString())
        }

        val url = builder.build()
        val body: String
        try {
            body = backendHttpClient.getText(url)
        } catch (error: HttpStatusException) {
            val message = parseExtractionError(error.responseBody)
            throw ExtractionException(message, error.statusCode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            throw ExtractionException(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "The WatchAll server could not be reached. Check its address in Settings.",
            )
        }

        val dto = gson.fromJson(body, ExtractResponseDto::class.java)
        if (dto.error != null && dto.streamUrl.isNullOrBlank()) {
            throw ExtractionException(dto.error)
        }
        val raw = dto.streamUrl?.trim().orEmpty()
        if (raw.isBlank()) {
            throw ExtractionException("The stream server did not return a playable video.")
        }

        val referer = dto.referer.orEmpty()
        val originalUrl = resolveRelative(raw)
        var type = normalizeType(dto.type)
        if (type.isBlank()) type = inferType(originalUrl)
        if (type.isBlank()) type = sniffType(originalUrl)
        if (type.isBlank()) type = "hls"
        val isEmbed = type == "embed"

        val variants = if (isEmbed) emptyList() else buildVariants(dto, referer)
        val captionsUrl = dto.captionsUrl?.let { resolveRelative(it) } ?: ""
        val captions = dto.captions.mapNotNull { toCaptionTrack(it, referer) }

        StreamResult(
            playbackUrl = if (isEmbed) originalUrl else proxyUrl(originalUrl, type, referer),
            originalUrl = originalUrl,
            type = if (isEmbed) "embed" else type,
            server = dto.server,
            source = source,
            referer = referer,
            variants = variants,
            variantIndex = 0,
            captionsUrl = captionsUrl,
            captions = captions,
        )
    }

    private fun buildVariants(dto: ExtractResponseDto, referer: String): List<StreamVariant> {
        val list = mutableListOf<StreamVariant>()
        val add = { urlRaw: String?, kind: String ->
            val url = urlRaw?.trim().orEmpty()
            if (url.isNotBlank()) {
                val resolved = resolveRelative(url)
                var type = normalizeType(kind)
                if (type.isBlank()) type = inferType(resolved)
                if (type.isBlank()) type = "mp4"
                val candidate = StreamVariant(
                    playbackUrl = proxyUrl(resolved, type, referer),
                    originalUrl = resolved,
                    type = type,
                    quality = 0,
                    codec = "",
                    verified = true,
                )
                if (list.none { it.playbackUrl == candidate.playbackUrl }) list += candidate
            }
        }

        // Collect every format the server returned. Candidates come first because
        // the server already verified them against the real browser player.
        dto.candidates.forEach { candidate ->
            val url = candidate.url?.trim().orEmpty()
            if (url.isBlank()) return@forEach
            val resolved = resolveRelative(url)
            val kind = candidate.type?.trim().orEmpty()
            var type = normalizeType(kind)
            if (type.isBlank()) type = inferType(resolved)
            if (type.isBlank()) type = "mp4"
            val variant = StreamVariant(
                playbackUrl = proxyUrl(resolved, type, referer),
                originalUrl = resolved,
                type = type,
                quality = candidate.quality ?: candidate.height ?: qualityFromUrl(resolved),
                codec = candidate.codec ?: codecFromUrl(resolved),
                verified = candidate.verified,
            )
            if (list.none { it.playbackUrl == variant.playbackUrl }) list += variant
        }

        add(dto.dash, "dash")
        add(dto.hls, "hls")
        dto.streams.forEach { stream ->
            val url = stream.url?.trim().orEmpty()
            if (url.isBlank()) return@forEach
            val resolved = resolveRelative(url)
            val type = normalizeType(stream.type?.trim().orEmpty()).ifBlank { inferType(resolved).ifBlank { "mp4" } }
            val variant = StreamVariant(
                playbackUrl = proxyUrl(resolved, type, referer),
                originalUrl = resolved,
                type = type,
                quality = stream.quality ?: stream.height ?: qualityFromUrl(resolved),
                codec = stream.codec ?: codecFromUrl(resolved),
                verified = true,
            )
            if (list.none { it.playbackUrl == variant.playbackUrl }) list += variant
        }
        add(dto.mp4, "mp4")
        add(dto.streamUrl, dto.type ?: "")

        // Fill quality/codec labels for candidates that lacked them.
        return list.map { variant ->
            variant.copy(
                quality = if (variant.quality > 0) variant.quality else qualityFromUrl(variant.originalUrl),
                codec = variant.codec.ifBlank { codecFromUrl(variant.originalUrl) },
            )
        }
    }

    private fun toCaptionTrack(dto: CaptionTrackDto, referer: String): CaptionTrack? {
        val raw = (dto.url ?: dto.src ?: dto.subtitleUrl ?: dto.captionUrl ?: dto.fileUrl)?.trim().orEmpty()
        if (raw.isBlank()) return null
        val resolved = resolveRelative(raw)
        val label = dto.label ?: dto.name ?: dto.title ?: "Subtitles"
        val language = dto.language ?: dto.lang ?: dto.languageCode ?: dto.locale ?: ""
        val format = dto.format ?: if (resolved.substringBefore('?').endsWith(".srt", true)) "srt" else "vtt"
        return CaptionTrack(
            url = resolved,
            label = label,
            language = language,
            format = format,
        )
    }

    private fun qualityFromUrl(value: String): Int {
        val match = Regex("(?:^|[^\\d])(2160|1440|1080|720|480|360|240)p(?:[^\\d]|\$)", RegexOption.IGNORE_CASE)
            .find(value)
        if (match != null) return match.groupValues[1].toIntOrNull() ?: 0
        val lower = value.lowercase()
        return when {
            "4k" in lower || "uhd" in lower -> 2160
            "fhd" in lower -> 1080
            "hd" in lower -> 720
            else -> 0
        }
    }

    private fun codecFromUrl(value: String): String {
        val lower = value.lowercase()
        return when {
            "hevc" in lower || "h265" in lower || "x265" in lower || "h.265" in lower -> "hevc"
            "h264" in lower || "avc" in lower || "x264" in lower || "h.264" in lower -> "h264"
            "av1" in lower -> "av1"
            else -> ""
        }
    }

    private fun parseExtractionError(body: String): String {
        return try {
            val value = gson.fromJson(body, ExtractResponseDto::class.java)?.error
            value?.takeIf { it.isNotBlank() }
                ?: "This server list could not find a playable stream. Try another list."
        } catch (_: Exception) {
            "This server list could not find a playable stream. Try another list."
        }
    }

    private fun normalizeType(value: String?): String {
        return when (value?.trim()?.lowercase(Locale.ROOT)) {
            "hls", "m3u8" -> "hls"
            "dash", "mpd" -> "dash"
            "mp4", "video" -> "mp4"
            "embed" -> "embed"
            else -> ""
        }
    }

    private fun inferType(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") -> "hls"
            path.endsWith(".mpd") -> "dash"
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") -> "mp4"
            else -> ""
        }
    }

    private suspend fun sniffType(url: String): String {
        return try {
            val infoUrl = endpoint("api/media-info").newBuilder()
                .addQueryParameter("url", url)
                .build()
            val body = backendHttpClient.getText(infoUrl)
            val info = gson.fromJson(body, MediaInfoDto::class.java)
            val contentType = info?.contentType.orEmpty().lowercase(Locale.ROOT)
            when {
                contentType.contains("mpegurl") || contentType.contains("m3u8") -> "hls"
                contentType.contains("dash+xml") || contentType.contains("mpd") -> "dash"
                contentType.startsWith("video/") || contentType.contains("octet-stream") -> "mp4"
                else -> ""
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ""
        }
    }
}
