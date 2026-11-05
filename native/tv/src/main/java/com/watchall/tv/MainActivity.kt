package com.watchall.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.watchall.tv.ui.WatchAllTvApp
import com.watchall.tv.ui.theme.WatchAllTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as WatchAllTvApplication).graph
        setContent {
            WatchAllTvTheme {
                WatchAllTvApp(graph)
            }
        }
    }
}
