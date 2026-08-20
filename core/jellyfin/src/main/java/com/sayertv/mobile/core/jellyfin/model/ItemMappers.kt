/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin.model

import com.sayertv.mobile.core.jellyfin.Session
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * BaseItemDto → domain mappers. The ONLY place SDK item types are unpacked.
 * Image URLs are fully resolved here (incl. auth token) so features and Coil
 * never need to know about the Jellyfin image API.
 */
object ItemMappers {

    fun BaseItemDto.toLibraryView(session: Session): LibraryView = LibraryView(
        id = id.toString(),
        name = name.orEmpty(),
        collectionType = when (collectionType) {
            CollectionType.MOVIES -> CollectionKind.MOVIES
            CollectionType.TVSHOWS -> CollectionKind.TVSHOWS
            CollectionType.MUSIC -> CollectionKind.MUSIC
            null -> CollectionKind.MIXED
            else -> CollectionKind.OTHER
        },
        imageUrl = primaryImageUrl(session),
    )

    fun BaseItemDto.toMediaItem(session: Session): MediaItem = MediaItem(
        id = id.toString(),
        name = name.orEmpty(),
        kind = when (type) {
            BaseItemKind.MOVIE -> MediaKind.MOVIE
            BaseItemKind.SERIES -> MediaKind.SERIES
            BaseItemKind.SEASON -> MediaKind.SEASON
            BaseItemKind.EPISODE -> MediaKind.EPISODE
            BaseItemKind.BOX_SET -> MediaKind.COLLECTION
            else -> MediaKind.OTHER
        },
        overview = overview,
        year = productionYear,
        runtimeMs = runTimeTicks.ticksToMs().takeIf { it > 0 },
        communityRating = communityRating,
        officialRating = officialRating,
        seriesId = seriesId?.toString(),
        seriesName = seriesName,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        played = userData?.played == true,
        playedPercentage = userData?.playedPercentage,
        resumePositionMs = userData?.playbackPositionTicks.ticksToMs(),
        unplayedCount = userData?.unplayedItemCount,
        primaryImageUrl = primaryImageUrl(session),
        backdropImageUrl = backdropImageUrl(session),
        genres = genres.orEmpty(),
        tags = tags.orEmpty(),
        providerIds = providerIds.orEmpty().filterValues { it != null }.mapValues { it.value!! },
        audioStreams = mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.AUDIO }
            .map { StreamInfo(it.index, it.language, it.displayTitle, it.isDefault) },
        subtitleStreams = mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE }
            .map { StreamInfo(it.index, it.language, it.displayTitle, it.isDefault) },
    )

    /** Episodes fall back to the series poster when they lack their own primary image. */
    private fun BaseItemDto.primaryImageUrl(session: Session): String? {
        val ownTag = imageTags?.get(ImageType.PRIMARY)
        return when {
            ownTag != null -> imageUrl(session, id.toString(), "Primary", ownTag)
            seriesPrimaryImageTag != null && seriesId != null ->
                imageUrl(session, seriesId.toString(), "Primary", seriesPrimaryImageTag)
            else -> null
        }
    }

    private fun BaseItemDto.backdropImageUrl(session: Session): String? {
        val tag = backdropImageTags?.firstOrNull()
            ?: return parentBackdropImageTags?.firstOrNull()?.let { parentTag ->
                parentBackdropItemId?.let { imageUrl(session, it.toString(), "Backdrop/0", parentTag) }
            }
        return imageUrl(session, id.toString(), "Backdrop/0", tag)
    }

    private fun imageUrl(session: Session, itemId: String, path: String, tag: String?): String =
        buildString {
            append(session.baseUrl)
            append("/Items/").append(itemId).append("/Images/").append(path)
            append("?fillWidth=480&quality=90")
            tag?.let { append("&tag=").append(it) }
            session.api.accessToken?.let { append("&api_key=").append(it) }
        }
}
