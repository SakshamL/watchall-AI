package com.watchall.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cache
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpStatusException(
    val statusCode: Int,
    val responseBody: String,
    url: String,
) : IOException("HTTP $statusCode for $url")

internal var httpCacheDir: File? = null

fun configureHttpCache(cacheDir: File) {
    httpCacheDir = cacheDir
}

internal val tmdbHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cache(httpCacheDir?.let { Cache(File(it, "tmdb_http_cache"), 32L * 1024 * 1024) })
        .build()
}

internal val backendHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cache(httpCacheDir?.let { Cache(File(it, "backend_http_cache"), 128L * 1024 * 1024) })
        .build()
}

internal suspend fun OkHttpClient.getText(
    url: HttpUrl,
    headers: Map<String, String> = emptyMap(),
): String {
    val builder = Request.Builder()
        .url(url)
        .get()
        .header("Accept", "application/json, text/plain, */*")
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 WatchAll/1.0",
        )
    headers.forEach { (key, value) -> builder.header(key, value) }
    val request = builder.build()

    return suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = try {
                        it.body.string()
                    } catch (error: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                        return
                    }
                    if (!it.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                HttpStatusException(it.code, body, url.toString()),
                            )
                        }
                        return
                    }
                    if (continuation.isActive) continuation.resume(body)
                }
            }
        })
    }
}
