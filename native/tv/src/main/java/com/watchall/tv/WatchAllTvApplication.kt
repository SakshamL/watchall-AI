package com.watchall.tv

import android.app.Application
import com.watchall.core.WatchAllGraph

class WatchAllTvApplication : Application() {
    lateinit var graph: WatchAllGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = WatchAllGraph(this)
    }
}
