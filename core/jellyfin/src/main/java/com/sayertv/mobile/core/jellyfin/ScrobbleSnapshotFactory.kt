/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.common.ScrobbleSnapshot
import com.sayertv.mobile.core.jellyfin.model.ItemMappers.toMediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.jellyfin.sdk.api.client.extensions.userLibraryApi

/**
 * Builds scrobble snapshots ENRICHED with series-level metadata.
 *
 * ALPHA10 BUG: episodes on Jellyfin rarely carry the AniList/AniDB provider
 * ids or the "Anime" genre — the SERIES does. Detection built from the bare
 * episode silently failed for manual mark-watched. This factory merges the
 * series' providerIds/genres/tags into episode snapshots (design doc §6.3
 * explicitly keys detection off the series).
 */
@Singleton
class ScrobbleSnapshotFactory @Inject constructor(
    private val sessionManager: SessionManager,
) {
    // One series fetch per series per process — cheap cache.
    private val seriesCache = mutableMapOf<String, MediaItem>()

    suspend fun create(item: MediaItem, positionMs: Long, durationMs: Long): ScrobbleSnapshot? {
        val session = sessionManager.current() ?: return null
        var genres = item.genres
        var tags = item.tags
        var providerIds = item.providerIds

        if (item.kind == MediaKind.EPISODE && item.seriesId != null) {
            val series = seriesCache[item.seriesId] ?: runCatching {
                session.api.userLibraryApi.getItem(itemId = UUID.fromString(item.seriesId))
                    .content.toMediaItem(session)
            }.getOrNull()?.also { seriesCache[item.seriesId!!] = it }
            if (series != null) {
                genres = (genres + series.genres).distinct()
                tags = (tags + series.tags).distinct()
                // Series ids win on key collision — they're the authoritative ones.
                providerIds = providerIds + series.providerIds
            }
        }

        return ScrobbleSnapshot(
            serverId = session.serverId,
            itemId = item.id,
            seriesId = item.seriesId,
            title = item.name,
            seriesName = item.seriesName,
            seasonNumber = item.seasonNumber,
            episodeNumber = item.episodeNumber,
            isMovie = item.kind == MediaKind.MOVIE,
            year = item.year,
            genres = genres,
            tags = tags,
            providerIds = providerIds,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }
}
