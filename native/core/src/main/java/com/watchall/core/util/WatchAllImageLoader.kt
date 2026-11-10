package com.watchall.core.util

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * One Coil loader for the whole app.
 *
 * The defaults let the memory cache grow to 25% of the heap and always fade
 * images in, which is fine on a phone but causes repeated pressure on a TV
 * stick. Budgets here scale with [DeviceProfile.tier], and the disk cache means
 * a poster already seen is decoded from local storage instead of the network.
 */
object WatchAllImageLoader {
    fun create(context: PlatformContext): ImageLoader {
        val appContext = context.applicationContext
        val tier = DeviceProfile.tier(appContext)

        val memoryPercent = when (tier) {
            DeviceTier.LOW -> 0.10
            DeviceTier.MID -> 0.20
            DeviceTier.HIGH -> 0.25
        }
        val diskBytes = when (tier) {
            DeviceTier.LOW -> 64L
            DeviceTier.MID -> 96L
            DeviceTier.HIGH -> 128L
        } * 1024L * 1024L

        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            // Fading every poster in costs a frame per image on weak GPUs and is
            // the first thing to drop when scrolling on a stick.
            .crossfade(tier != DeviceTier.LOW)
            .build()
    }
}
