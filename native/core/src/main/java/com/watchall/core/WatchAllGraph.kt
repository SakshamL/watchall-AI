package com.watchall.core

import android.content.Context
import com.watchall.core.net.TmdbClient
import com.watchall.core.net.WatchAllApi
import com.watchall.core.prefs.PreferencesStore
import com.watchall.core.repo.WatchAllRepository

class WatchAllGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val preferences = PreferencesStore(appContext)
    val api = WatchAllApi(preferences)
    val repository = WatchAllRepository(
        tmdb = TmdbClient(),
        api = api,
        preferences = preferences,
    )
}
