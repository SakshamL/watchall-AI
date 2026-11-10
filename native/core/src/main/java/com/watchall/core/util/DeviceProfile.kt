package com.watchall.core.util

import android.app.ActivityManager
import android.content.Context

/**
 * Rough hardware class of the device we are running on.
 *
 * Smoothness on a Fire TV Stick and on a flagship phone need opposite settings:
 * the stick has ~1 GB of usable RAM and a weak decoder, the phone has plenty of
 * both. Instead of one compromise, every tunable (player buffer size, image
 * cache budget, crossfade, 1080p upgrade) reads this tier.
 */
enum class DeviceTier {
    LOW,
    MID,
    HIGH,
}

object DeviceProfile {
    @Volatile
    private var cachedTier: DeviceTier? = null

    fun tier(context: Context): DeviceTier =
        cachedTier ?: synchronized(this) {
            cachedTier ?: detect(context.applicationContext).also { cachedTier = it }
        }

    private fun detect(context: Context): DeviceTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return DeviceTier.MID
        // Android's own flag for "this device should behave like a low-memory
        // device". TV sticks and budget boxes set it.
        if (activityManager.isLowRamDevice) return DeviceTier.LOW

        val totalMb = runCatching {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            info.totalMem / (1024L * 1024L)
        }.getOrDefault(0L)

        val heapMb = activityManager.memoryClass
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            totalMb in 1..2048 -> DeviceTier.LOW
            heapMb <= 192 -> DeviceTier.LOW
            totalMb in 1..3584 -> DeviceTier.MID
            heapMb <= 320 -> DeviceTier.MID
            cores <= 4 -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }
    }
}
