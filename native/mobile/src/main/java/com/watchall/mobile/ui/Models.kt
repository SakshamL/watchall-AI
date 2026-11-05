package com.watchall.mobile.ui

import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.SourceList

fun MediaSummary.toPlayerRequest(): PlayerRequest = PlayerRequest(
    mediaId = id,
    title = displayTitle,
    mediaType = mediaType,
    posterPath = posterPath,
    backdropPath = backdropPath,
    originalLanguage = originalLanguage,
    initialSource = SourceList.SERVER_ONE,
)

fun MediaDetails.toPlayerRequest(
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
): PlayerRequest = PlayerRequest(
    mediaId = id,
    title = displayTitle,
    mediaType = mediaType,
    posterPath = posterPath,
    backdropPath = backdropPath,
    originalLanguage = originalLanguage,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeTitle = episodeTitle,
    initialSource = SourceList.SERVER_ONE,
)
