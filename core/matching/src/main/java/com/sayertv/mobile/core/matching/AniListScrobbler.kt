/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.matching

import com.sayertv.mobile.core.anilist.AniListRepository
import com.sayertv.mobile.core.common.ScrobbleSink
import com.sayertv.mobile.core.common.ScrobbleSnapshot
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AniList scrobbler (design doc §6.3/§6.5):
 *  - watched threshold: position ≥ 90% of duration, fires ONCE per item
 *  - manual mark-watched fires immediately (product decision #3)
 *  - anime detection: AniList/AniDB provider ids, or Anime genre/tag
 *  - resolution via the matching pipeline; writes go to the durable queue
 */
@Singleton
class AniListScrobbler @Inject constructor(
    private val pipeline: MatchingPipeline,
    private val aniListRepository: AniListRepository,
) : ScrobbleSink {

    private val fired: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override suspend fun onPlaybackProgress(snapshot: ScrobbleSnapshot) {
        if (snapshot.durationMs <= 0) return
        if (snapshot.positionMs < snapshot.durationMs * WATCHED_THRESHOLD) return
        scrobble(snapshot)
    }

    override suspend fun onMarkedWatched(snapshot: ScrobbleSnapshot) {
        scrobble(snapshot)
    }

    private suspend fun scrobble(snapshot: ScrobbleSnapshot) {
        if (!fired.add(snapshot.itemId)) return  // already handled this session
        if (!aniListRepository.isLinked()) return
        if (!isAnime(snapshot)) {
            // Visible in Sync history so "nothing happened" is never a mystery.
            aniListRepository.recordSkipped(
                snapshot.seriesName ?: snapshot.title,
                snapshot.episodeLabel(),
                "NOT_DETECTED_AS_ANIME",
            )
            return
        }

        // ALPHA13 BUG: marking a whole series/season watched has no episode
        // number; it was silently treated as "episode 1" and always skipped.
        // Series-level marks now mean "set the entry to COMPLETED".
        val isSeriesLevel = snapshot.episodeNumber == null && !snapshot.isMovie

        val ref = snapshot.toEpisodeRef()
        when (val result = runCatching { pipeline.resolve(ref) }.getOrNull()) {
            is MatchResult.Matched -> {
                if (isSeriesLevel) {
                    aniListRepository.enqueueComplete(
                        anilistMediaId = result.anilistMediaId,
                        seriesTitle = snapshot.seriesName ?: snapshot.title,
                    )
                } else if (result.anilistEpisode >= 1) {
                    aniListRepository.enqueueProgress(
                        anilistMediaId = result.anilistMediaId,
                        episode = result.anilistEpisode,
                        seriesTitle = snapshot.seriesName ?: snapshot.title,
                        episodeLabel = snapshot.episodeLabel(),
                    )
                }
            }
            is MatchResult.NeedsReview -> aniListRepository.recordSkipped(
                snapshot.seriesName ?: snapshot.title,
                snapshot.episodeLabel(),
                "NEEDS_REVIEW",
            )
            else -> aniListRepository.recordSkipped(
                snapshot.seriesName ?: snapshot.title,
                snapshot.episodeLabel(),
                "NO_MATCH",
            )
        }
    }

    /** Detection stages per design doc §6.3 (cheap → cheap). */
    private fun isAnime(snapshot: ScrobbleSnapshot): Boolean {
        val providerHit = snapshot.providerIds.keys.any {
            it.equals("AniList", true) || it.equals("AniDB", true)
        }
        if (providerHit) return true
        val tagHit = (snapshot.genres + snapshot.tags).any { it.equals("anime", true) }
        return tagHit
    }

    private fun ScrobbleSnapshot.toEpisodeRef() = JellyfinEpisodeRef(
        serverId = serverId,
        itemId = itemId,
        seriesId = seriesId ?: itemId,
        seriesName = seriesName ?: title,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        productionYear = year,
        providerIds = providerIds,
        genres = genres,
        tags = tags,
        isMovie = isMovie,
    )

    private fun ScrobbleSnapshot.episodeLabel(): String = buildString {
        seasonNumber?.let { append("S$it") }
        episodeNumber?.let { append("E$it") }
        if (isEmpty()) append(title) else append(" · ").append(title)
    }

    private companion object {
        const val WATCHED_THRESHOLD = 0.90
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MatchingModule {
    @Binds abstract fun pipeline(impl: MatchingPipelineImpl): MatchingPipeline
    @Binds abstract fun scrobbleSink(impl: AniListScrobbler): ScrobbleSink
}
