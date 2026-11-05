package com.watchall.mobile

import android.app.Application
import com.watchall.core.WatchAllGraph

class WatchAllApplication : Application() {
    lateinit var graph: WatchAllGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = WatchAllGraph(this)
    }
}
