/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin.model

/**
 * Domain models exposed to feature modules. Golden rule: no Jellyfin SDK
 * types outside :core:jellyfin — features see only these.
 */
data class LibraryView(
    val id: String,
    val name: String,
    val collectionType: CollectionKind,
    val imageUrl: String?,
)

enum class CollectionKind { MOVIES, TVSHOWS, MUSIC, MIXED, OTHER }

enum class MediaKind { MOVIE, SERIES, SEASON, EPISODE, COLLECTION, OTHER }

data class MediaItem(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val overview: String?,
    val year: Int?,
    val runtimeMs: Long?,
    val communityRating: Float?,
    val officialRating: String?,
    // Episode context
    val seriesId: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    // User data
    val played: Boolean,
    val playedPercentage: Double?,
    val resumePositionMs: Long,
    val unplayedCount: Int?,
    // Presentation
    val primaryImageUrl: String?,
    val backdropImageUrl: String?,
    // Metadata for the M4 matching pipeline (design doc §6.3)
    val genres: List<String>,
    val tags: List<String>,
    val providerIds: Map<String, String>,
    // Stream metadata (pre-playback audio/subtitle pickers)
    val audioStreams: List<StreamInfo> = emptyList(),
    val subtitleStreams: List<StreamInfo> = emptyList(),
) {
    val isResumable: Boolean get() = resumePositionMs > 0 && !played
    val progressFraction: Float?
        get() = playedPercentage?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
}

data class StreamInfo(
    val index: Int,
    val language: String?,       // e.g. "jpn"
    val displayTitle: String?,   // e.g. "Japanese - AAC - Stereo"
    val isDefault: Boolean,
) {
    val label: String get() = displayTitle ?: language ?: "Track $index"
}

data class HomeSections(
    val libraries: List<LibraryView>,
    val continueWatching: List<MediaItem>,
    val nextUp: List<MediaItem>,
    val latest: List<LatestSection>,
)

data class LatestSection(val library: LibraryView, val items: List<MediaItem>)

data class SeriesDetail(
    val series: MediaItem,
    val seasons: List<MediaItem>,
)

/** Ticks are Jellyfin's 100-nanosecond unit. */
internal fun Long?.ticksToMs(): Long = (this ?: 0L) / 10_000L
