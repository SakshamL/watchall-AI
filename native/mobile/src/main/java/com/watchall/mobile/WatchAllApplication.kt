package com.watchall.mobile

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.watchall.core.WatchAllGraph
import com.watchall.core.util.WatchAllImageLoader

class WatchAllApplication : Application(), SingletonImageLoader.Factory {
    lateinit var graph: WatchAllGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = WatchAllGraph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        WatchAllImageLoader.create(context)

    /**
     * Hand the poster cache back when the system is genuinely short of memory
     * (or the app moved to the background) so it is not killed mid-session.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL &&
            level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }
}
