package com.watchall.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.watchall.core.WatchAllGraph
import com.watchall.core.data.HomeRow
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.SourceList
import com.watchall.core.data.toPlayerRequest
import com.watchall.core.vm.CollectionViewModel
import com.watchall.core.vm.DetailsViewModel
import com.watchall.core.vm.HomeViewModel
import com.watchall.core.vm.PlayerViewModel
import com.watchall.core.vm.SearchViewModel
import com.watchall.core.vm.SettingsViewModel
import com.watchall.mobile.ui.components.SourcePickerDialog
import com.watchall.mobile.ui.screens.DetailsScreen
import com.watchall.mobile.ui.screens.HomeScreen
import com.watchall.mobile.ui.screens.BrowseScreen
import com.watchall.mobile.ui.screens.CollectionScreen
import com.watchall.mobile.ui.screens.LibraryScreen
import com.watchall.mobile.ui.screens.PlayerScreen
import com.watchall.mobile.ui.screens.SearchScreen
import com.watchall.mobile.ui.screens.SettingsScreen
import com.watchall.mobile.ui.theme.WatchColors

private enum class RootTab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    BROWSE("Browse", Icons.Filled.Explore, Icons.Outlined.Explore),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("Library", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

private sealed interface Overlay {
    data class Details(val mediaId: Int, val mediaType: MediaType) : Overlay
    data class Collection(val row: HomeRow) : Overlay
    data class Player(val request: PlayerRequest, val sessionId: Long) : Overlay
}

@Composable
fun WatchAllMobileApp(graph: WatchAllGraph) {
    var tab by rememberSaveable { mutableStateOf(RootTab.HOME) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    var sessionCounter by remember { mutableLongStateOf(0L) }
    var pendingPlay by remember { mutableStateOf<PlayerRequest?>(null) }
    val tabStateHolder = rememberSaveableStateHolder()

    val homeViewModel = watchAllViewModel<HomeViewModel> {
        HomeViewModel(graph.repository)
    }
    val searchViewModel = watchAllViewModel<SearchViewModel> {
        SearchViewModel(graph.repository)
    }

    val topOverlay = overlays.lastOrNull()

    fun refreshAfterPlayer() {
        homeViewModel.refreshContinueWatching()
    }

    fun goBack() {
        if (pendingPlay != null) {
            pendingPlay = null
            return
        }
        val removed = if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) else null
        if (removed is Overlay.Player) refreshAfterPlayer()
        if (removed == null && tab != RootTab.HOME) tab = RootTab.HOME
    }

    fun openDetails(mediaId: Int, mediaType: MediaType) {
        overlays.add(Overlay.Details(mediaId, mediaType))
    }

    // Playback always goes through the server picker first, mirroring the TV
    // app and the web player. The chosen list is stored on the request so the
    // player starts its extraction from the list the user picked.
    fun openPlayer(request: PlayerRequest) {
        pendingPlay = request
    }

    fun startPlayback(request: PlayerRequest, source: SourceList) {
        pendingPlay = null
        sessionCounter += 1
        overlays.add(Overlay.Player(request.copy(initialSource = source), sessionCounter))
    }

    fun resume(entry: ResumeEntry) {
        val request = entry.toPlayerRequest()
        if (request != null) {
            openPlayer(request)
        } else {
            val id = entry.mediaKey.substringAfter('-').substringBefore('-').toIntOrNull()
            if (id != null) openDetails(id, entry.mediaType)
        }
    }

    BackHandler(enabled = pendingPlay != null || overlays.isNotEmpty() || tab != RootTab.HOME) {
        goBack()
    }

    AnimatedContent(
        targetState = topOverlay,
        transitionSpec = {
            // PlayerView is a SurfaceView, so fading it cannot alpha-blend and
            // would show a black flash. Everything else gets a fast 140 ms fade.
            val involvesPlayer = initialState is Overlay.Player || targetState is Overlay.Player
            val transform = if (involvesPlayer) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(140)) togetherWith fadeOut(tween(90))
            }
            transform.using(SizeTransform(clip = false))
        },
        label = "mobile-overlay",
    ) { activeOverlay ->
        when (activeOverlay) {
            is Overlay.Details -> {
                val detailsViewModel = watchAllViewModel<DetailsViewModel>(
                    key = "details-${activeOverlay.mediaType.apiValue}-${activeOverlay.mediaId}",
                ) {
                    DetailsViewModel(graph.repository, activeOverlay.mediaId, activeOverlay.mediaType)
                }
                DetailsScreen(
                    viewModel = detailsViewModel,
                    onBack = ::goBack,
                    onPlay = ::openPlayer,
                    onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                )
            }

            is Overlay.Collection -> {
                val collectionViewModel = watchAllViewModel<CollectionViewModel>(
                    key = "collection-${activeOverlay.row.id}",
                ) {
                    CollectionViewModel(graph.repository, activeOverlay.row)
                }
                CollectionScreen(
                    viewModel = collectionViewModel,
                    onBack = ::goBack,
                    onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                )
            }

            is Overlay.Player -> {
                val playerViewModel = watchAllViewModel<PlayerViewModel>(
                    key = "player-${activeOverlay.sessionId}",
                ) {
                    PlayerViewModel(graph.repository, activeOverlay.request)
                }
                PlayerScreen(
                    viewModel = playerViewModel,
                    onBack = ::goBack,
                )
            }

            null -> {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = WatchColors.Ink,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar(
                            containerColor = WatchColors.Ink,
                            tonalElevation = 0.dp,
                        ) {
                            RootTab.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = { tab = item },
                                    icon = {
                                        Icon(
                                            imageVector = if (tab == item) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                        )
                                    },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = WatchColors.Ink,
                                        selectedTextColor = WatchColors.Cyan,
                                        indicatorColor = WatchColors.Cyan,
                                        unselectedIconColor = WatchColors.TextMuted,
                                        unselectedTextColor = WatchColors.TextMuted,
                                    ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                    ) {
                        Crossfade(
                            targetState = tab,
                            animationSpec = tween(durationMillis = 120),
                            label = "mobile-tab",
                        ) { activeTab ->
                            tabStateHolder.SaveableStateProvider(activeTab.name) {
                                when (activeTab) {
                                    RootTab.HOME -> HomeScreen(
                                        viewModel = homeViewModel,
                                        onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                        onPlay = ::openPlayer,
                                        onResume = ::resume,
                                        onViewMore = { row -> overlays.add(Overlay.Collection(row)) },
                                        onRemoveContinueWatching = homeViewModel::removeContinueWatching,
                                        onOpenSearch = { tab = RootTab.SEARCH },
                                        onOpenSettings = { tab = RootTab.SETTINGS },
                                    )

                                    RootTab.SEARCH -> SearchScreen(
                                        viewModel = searchViewModel,
                                        onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                    )

                                    RootTab.BROWSE -> BrowseScreen(
                                        graph = graph,
                                        onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                    )

                                    RootTab.LIBRARY -> LibraryScreen(
                                        repository = graph.repository,
                                        onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                        onResume = ::resume,
                                        onContinueWatchingRemoved = homeViewModel::refreshContinueWatching,
                                    )

                                    RootTab.SETTINGS -> {
                                        val settingsViewModel = watchAllViewModel<SettingsViewModel> {
                                            SettingsViewModel(graph.repository)
                                        }
                                        SettingsScreen(viewModel = settingsViewModel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingPlay?.let { request ->
        SourcePickerDialog(
            mediaType = request.mediaType,
            initialSource = request.initialSource,
            onSelect = { source -> startPlayback(request, source) },
            onDismiss = { pendingPlay = null },
        )
    }
}
