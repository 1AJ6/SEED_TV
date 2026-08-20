/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.common

/**
 * Playback → scrobbler boundary (design doc §6.5). :core:playback and
 * :core:jellyfin emit snapshots; the AniList scrobbler (bound via Hilt in
 * :core:matching) consumes them. Keeps playback code free of AniList types.
 */
data class ScrobbleSnapshot(
    val serverId: String,
    val itemId: String,
    val seriesId: String?,
    val title: String,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val isMovie: Boolean,
    val year: Int?,
    val genres: List<String>,
    val tags: List<String>,
    val providerIds: Map<String, String>,
    val positionMs: Long,
    val durationMs: Long,
)

interface ScrobbleSink {
    /** Called on every playback progress tick (~10s). Sink applies the watched threshold. */
    suspend fun onPlaybackProgress(snapshot: ScrobbleSnapshot)

    /** Manual "mark watched" — product decision #3: scrobbles through the same pipeline. */
    suspend fun onMarkedWatched(snapshot: ScrobbleSnapshot)
}
