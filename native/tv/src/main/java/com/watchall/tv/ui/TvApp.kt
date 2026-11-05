package com.watchall.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchall.core.WatchAllGraph
import com.watchall.core.data.HomeRow
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.toPlayerRequest
import com.watchall.core.vm.CollectionViewModel
import com.watchall.core.vm.DetailsViewModel
import com.watchall.core.vm.HomeViewModel
import com.watchall.core.vm.PersonViewModel
import com.watchall.core.vm.PlayerViewModel
import com.watchall.core.vm.SearchViewModel
import com.watchall.core.vm.SettingsViewModel
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.LocalTvNavFocusRequester
import com.watchall.tv.ui.components.TvBrand
import com.watchall.tv.ui.components.TvFocusable
import com.watchall.tv.ui.screens.TvBrowseScreen
import com.watchall.tv.ui.screens.TvCollectionScreen
import com.watchall.tv.ui.screens.TvDetailsScreen
import com.watchall.tv.ui.screens.TvHomeScreen
import com.watchall.tv.ui.screens.TvLibraryScreen
import com.watchall.tv.ui.screens.TvPersonScreen
import com.watchall.tv.ui.screens.TvPlayerScreen
import com.watchall.tv.ui.screens.TvSearchScreen
import com.watchall.tv.ui.screens.TvSettingsScreen
import com.watchall.tv.ui.theme.TvColors
import kotlinx.coroutines.delay

private enum class TvTab(
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

private sealed interface TvOverlay {
    data class Details(val mediaId: Int, val mediaType: MediaType) : TvOverlay
    data class Collection(val row: HomeRow) : TvOverlay
    data class Person(
        val personId: Int,
        val name: String,
        val profilePath: String?,
    ) : TvOverlay
    data class Player(val request: PlayerRequest, val sessionId: Long) : TvOverlay
}

@Composable
fun WatchAllTvApp(graph: WatchAllGraph) {
    var tab by rememberSaveable { mutableStateOf(TvTab.HOME) }
    val overlays = remember { mutableStateListOf<TvOverlay>() }
    var sessionCounter by remember { mutableLongStateOf(0L) }
    val navFocusRequesters = remember {
        TvTab.entries.associateWith { FocusRequester() }
    }
    val contentFocusRequesters = remember {
        TvTab.entries.associateWith { FocusRequester() }
    }
    val tabStateHolder = rememberSaveableStateHolder()

    val homeViewModel = watchAllTvViewModel<HomeViewModel> {
        HomeViewModel(graph.repository)
    }
    val searchViewModel = watchAllTvViewModel<SearchViewModel> {
        SearchViewModel(graph.repository)
    }

    fun goBack() {
        val removed = if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) else null
        if (removed is TvOverlay.Player) homeViewModel.refreshContinueWatching()
        if (removed == null && tab != TvTab.HOME) tab = TvTab.HOME
    }

    fun openDetails(mediaId: Int, mediaType: MediaType) {
        overlays.add(TvOverlay.Details(mediaId, mediaType))
    }

    fun openPlayer(request: PlayerRequest) {
        sessionCounter += 1
        overlays.add(TvOverlay.Player(request, sessionCounter))
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

    BackHandler(enabled = overlays.isNotEmpty() || tab != TvTab.HOME) {
        goBack()
    }

    // Whenever the tab bar becomes the visible layer (first launch, tab change,
    // or returning from a details/player overlay) put focus back on its selected
    // item. Down from there jumps straight into the screen's primary action.
    LaunchedEffect(tab, overlays.isEmpty()) {
        if (overlays.isEmpty()) {
            delay(70)
            runCatching { navFocusRequesters.getValue(tab).requestFocus() }
        }
    }

    when (val overlay = overlays.lastOrNull()) {
        is TvOverlay.Details -> {
            val detailsViewModel = watchAllTvViewModel<DetailsViewModel>(
                key = "tv-details-${overlay.mediaType.apiValue}-${overlay.mediaId}",
            ) {
                DetailsViewModel(graph.repository, overlay.mediaId, overlay.mediaType)
            }
            TvDetailsScreen(
                viewModel = detailsViewModel,
                onBack = ::goBack,
                onPlay = ::openPlayer,
                onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                onOpenPerson = { cast ->
                    overlays.add(
                        TvOverlay.Person(
                            personId = cast.id,
                            name = cast.name,
                            profilePath = cast.profilePath,
                        ),
                    )
                },
            )
        }

        is TvOverlay.Person -> {
            val personViewModel = watchAllTvViewModel<PersonViewModel>(
                key = "tv-person-${overlay.personId}",
            ) {
                PersonViewModel(
                    repository = graph.repository,
                    personId = overlay.personId,
                    name = overlay.name,
                    profilePath = overlay.profilePath,
                )
            }
            TvPersonScreen(
                viewModel = personViewModel,
                onBack = ::goBack,
                onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
            )
        }

        is TvOverlay.Collection -> {
            val collectionViewModel = watchAllTvViewModel<CollectionViewModel>(
                key = "tv-collection-${overlay.row.id}",
            ) {
                CollectionViewModel(graph.repository, overlay.row)
            }
            TvCollectionScreen(
                viewModel = collectionViewModel,
                onBack = ::goBack,
                onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
            )
        }

        is TvOverlay.Player -> {
            val playerViewModel = watchAllTvViewModel<PlayerViewModel>(
                key = "tv-player-${overlay.sessionId}",
            ) {
                PlayerViewModel(graph.repository, overlay.request)
            }
            TvPlayerScreen(
                viewModel = playerViewModel,
                onBack = ::goBack,
            )
        }

        null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvColors.Ink),
            ) {
                TvTopNav(
                    selected = tab,
                    onSelect = { tab = it },
                    focusRequesters = navFocusRequesters,
                    contentFocusRequester = contentFocusRequesters.getValue(tab),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(TvColors.DeepInk, TvColors.Ink),
                            ),
                        ),
                ) {
                    CompositionLocalProvider(
                        LocalTvContentFocusRequester provides contentFocusRequesters.getValue(tab),
                        LocalTvNavFocusRequester provides navFocusRequesters.getValue(tab),
                    ) {
                        tabStateHolder.SaveableStateProvider(tab.name) {
                            when (tab) {
                                TvTab.HOME -> TvHomeScreen(
                                    viewModel = homeViewModel,
                                    onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                    onPlay = ::openPlayer,
                                    onResume = ::resume,
                                    onViewMore = { row -> overlays.add(TvOverlay.Collection(row)) },
                                )

                                TvTab.BROWSE -> TvBrowseScreen(
                                    graph = graph,
                                    onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                )

                                TvTab.SEARCH -> TvSearchScreen(
                                    viewModel = searchViewModel,
                                    onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                )

                                TvTab.LIBRARY -> TvLibraryScreen(
                                    repository = graph.repository,
                                    onOpenDetails = { media -> openDetails(media.id, media.mediaType) },
                                    onPlay = ::openPlayer,
                                    onBrowse = { tab = TvTab.BROWSE },
                                )

                                TvTab.SETTINGS -> {
                                    val settingsViewModel = watchAllTvViewModel<SettingsViewModel> {
                                        SettingsViewModel(graph.repository)
                                    }
                                    TvSettingsScreen(viewModel = settingsViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTopNav(
    selected: TvTab,
    onSelect: (TvTab) -> Unit,
    focusRequesters: Map<TvTab, FocusRequester>,
    contentFocusRequester: FocusRequester,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(TvColors.DeepInk)
                .padding(horizontal = 32.dp)
                // Down from the bar goes to the current screen's primary action.
                // The requester may not be attached yet (for example while Home
                // is still showing its first-load skeleton), so the request is
                // guarded and an unconsumed event falls back to Compose focus
                // search instead of throwing.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        runCatching { contentFocusRequester.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvBrand()
            Spacer(Modifier.width(20.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Spacer(Modifier.width(16.dp))
            TvTab.entries.forEach { item ->
                val isSelected = item == selected
                TvFocusable(
                    onClick = { onSelect(item) },
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    focusRequester = focusRequesters.getValue(item),
                    scaleOnFocus = 1.02f,
                    containerColor = if (isSelected) {
                        TvColors.Cyan.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    },
                    focusedContainerColor = TvColors.CardHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = if (isSelected) TvColors.Cyan else TvColors.TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) TvColors.Cyan else TvColors.TextMuted,
                            fontWeight = if (isSelected) MaterialTheme.typography.labelLarge.fontWeight else null,
                        )
                    }
                }
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "PRIVATE · AD-FREE",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = TvColors.TextMuted.copy(alpha = 0.45f),
                letterSpacing = 1.1.sp,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.07f)),
        )
    }
}
