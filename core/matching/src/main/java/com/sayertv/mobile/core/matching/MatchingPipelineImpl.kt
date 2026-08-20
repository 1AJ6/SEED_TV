/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.matching

import com.sayertv.mobile.core.anilist.AniListApi
import com.sayertv.mobile.core.anilist.AniListCandidate
import com.sayertv.mobile.core.database.dao.MediaMappingDao
import com.sayertv.mobile.core.database.entity.MediaMappingEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * AniList resolution pipeline v1 (design doc §6.4).
 * Stage order, first hit wins:
 *   0. Room cache (media_mapping)
 *   1. Jellyfin AniList provider id (metadata plugin) — authoritative
 *   2. Scored title search (romaji/english/synonyms + year + episode sanity)
 *   3. Season/absolute-episode remap by walking the SEQUEL chain
 * (AniDB offline-bridge stage lands post-alpha with the bundled mapping DB.)
 */
@Singleton
class MatchingPipelineImpl @Inject constructor(
    private val api: AniListApi,
    private val mappingDao: MediaMappingDao,
) : MatchingPipeline {

    override suspend fun resolve(episode: JellyfinEpisodeRef): MatchResult {
        val seasonNumber = episode.seasonNumber ?: 1
        val episodeNumber = episode.episodeNumber ?: 1

        // Stage 0: cache
        mappingDao.find(episode.serverId, episode.seriesId, seasonNumber)?.let { cached ->
            if (!cached.syncEnabled) return MatchResult.NotApplicable
            return MatchResult.Matched(
                anilistMediaId = cached.anilistMediaId,
                anilistEpisode = episodeNumber - cached.episodeOffset,
                confirmed = cached.confirmed,
                method = MatchMethod.valueOf(cached.matchMethod),
                score = cached.matchScore,
            )
        }

        // Stage 1: direct provider id from the Jellyfin AniList metadata plugin
        episode.providerIds.entries
            .firstOrNull { it.key.equals("AniList", ignoreCase = true) }
            ?.value?.toIntOrNull()
            ?.let { anilistId ->
                val (mediaId, offset) = remapForEpisode(anilistId, episodeNumber)
                cache(episode, seasonNumber, mediaId, offset, MatchMethod.PROVIDER_ID, 1.0, confirmed = true)
                return MatchResult.Matched(mediaId, episodeNumber - offset, true, MatchMethod.PROVIDER_ID, 1.0)
            }

        // Stage 2: scored title search
        val searchTitle = buildSearchTitle(episode.seriesName, seasonNumber)
        val candidates = runCatching { api.search(searchTitle) }.getOrNull()
            ?: return MatchResult.NotApplicable
        val scored = candidates
            .map { it to score(it, episode) }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return MatchResult.NotApplicable

        if (best.second < ACCEPT_THRESHOLD) {
            return MatchResult.NeedsReview(
                scored.take(5).map { (candidate, sc) ->
                    Candidate(candidate.id, candidate.titleRomaji ?: candidate.titleEnglish ?: "?", candidate.year, candidate.episodes, sc)
                },
            )
        }

        // Stage 3: episode remap across the sequel chain (multi-cour / absolute numbering)
        val (mediaId, offset) = remapForEpisode(best.first.id, episodeNumber)
        cache(episode, seasonNumber, mediaId, offset, MatchMethod.TITLE_SEARCH, best.second, confirmed = false)
        return MatchResult.Matched(mediaId, episodeNumber - offset, false, MatchMethod.TITLE_SEARCH, best.second)
    }

    override suspend fun confirmManual(episode: JellyfinEpisodeRef, anilistMediaId: Int, episodeOffset: Int) {
        cache(
            episode,
            episode.seasonNumber ?: 1,
            anilistMediaId,
            episodeOffset,
            MatchMethod.MANUAL,
            1.0,
            confirmed = true,
        )
    }

    /**
     * If [episodeNumber] exceeds the entry's episode count, walk SEQUEL
     * relations subtracting counts — turns absolute S1E37 into (sequel, E13).
     * Returns (mediaId, episodeOffset).
     */
    private suspend fun remapForEpisode(startId: Int, episodeNumber: Int): Pair<Int, Int> {
        var mediaId = startId
        var offset = 0
        var hops = 0
        while (hops < MAX_SEQUEL_HOPS) {
            val (episodes, sequelIds) = runCatching { api.sequels(mediaId) }.getOrNull()
                ?: return mediaId to offset
            if (episodes == null || episodes <= 0) return mediaId to offset
            if (episodeNumber - offset <= episodes) return mediaId to offset
            val next = sequelIds.firstOrNull() ?: return mediaId to offset
            offset += episodes
            mediaId = next
            hops++
        }
        return mediaId to offset
    }

    private fun buildSearchTitle(seriesName: String, seasonNumber: Int): String =
        if (seasonNumber <= 1) seriesName else "$seriesName Season $seasonNumber"

    private fun score(candidate: AniListCandidate, episode: JellyfinEpisodeRef): Double {
        val target = normalize(episode.seriesName)
        val names = buildList {
            candidate.titleRomaji?.let { add(normalize(it)) }
            candidate.titleEnglish?.let { add(normalize(it)) }
            candidate.synonyms.forEach { add(normalize(it)) }
        }
        var best = names.maxOfOrNull { titleSimilarity(target, it) } ?: 0.0
        // Year proximity bonus/penalty
        val productionYear = episode.productionYear
        val candidateYear = candidate.year
        if (productionYear != null && candidateYear != null) {
            val diff = abs(productionYear - candidateYear)
            best += when {
                diff == 0 -> 0.1
                diff == 1 -> 0.05
                diff > 3 -> -0.15
                else -> 0.0
            }
        }
        // Format sanity: movies should match MOVIE entries
        if (episode.isMovie && candidate.format == "MOVIE") best += 0.05
        if (!episode.isMovie && candidate.format == "MOVIE") best -= 0.1
        return best.coerceIn(0.0, 1.0)
    }

    private fun titleSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a.contains(b) || b.contains(a)) return 0.85
        val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size.toDouble()
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private suspend fun cache(
        episode: JellyfinEpisodeRef,
        seasonNumber: Int,
        anilistMediaId: Int,
        offset: Int,
        method: MatchMethod,
        score: Double,
        confirmed: Boolean,
    ) {
        mappingDao.upsert(
            MediaMappingEntity(
                serverId = episode.serverId,
                seriesId = episode.seriesId,
                seasonNumber = seasonNumber,
                anilistMediaId = anilistMediaId,
                episodeOffset = offset,
                confirmed = confirmed,
                matchMethod = method.name,
                matchScore = score,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val ACCEPT_THRESHOLD = 0.6
        const val MAX_SEQUEL_HOPS = 8
    }
}
