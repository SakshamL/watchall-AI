package com.watchall.tv.ui

import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.SourceList

fun MediaSummary.toPlayerRequest(
    initialSource: SourceList = SourceList.SERVER_ONE,
): PlayerRequest = PlayerRequest(
    mediaId = id,
    title = displayTitle,
    mediaType = mediaType,
    posterPath = posterPath,
    backdropPath = backdropPath,
    originalLanguage = originalLanguage,
    initialSource = initialSource,
)

fun MediaDetails.toPlayerRequest(
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
    initialSource: SourceList = SourceList.SERVER_ONE,
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
    initialSource = initialSource,
)
