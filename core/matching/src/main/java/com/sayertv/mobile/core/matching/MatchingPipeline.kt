/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.matching

/**
 * Anime detection + AniList resolution pipeline contracts (design doc §6.3–§6.4).
 * Full implementation lands in M4; contracts are frozen now because
 * :feature:player (M2/M3) will call [MatchingPipeline.resolve] on playback start.
 */
data class JellyfinEpisodeRef(
    val serverId: String,
    val itemId: String,
    val seriesId: String,
    val seriesName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val productionYear: Int?,
    val providerIds: Map<String, String>,   // "AniList", "AniDB", "Tvdb", ...
    val genres: List<String>,
    val tags: List<String>,
    val isMovie: Boolean,
)

sealed interface MatchResult {
    /** Resolved: sync to this AniList entry/episode. */
    data class Matched(
        val anilistMediaId: Int,
        val anilistEpisode: Int,
        val confirmed: Boolean,
        val method: MatchMethod,
        val score: Double,
    ) : MatchResult

    /** Anime, but ambiguous — surfaced in the "Review match" UI. */
    data class NeedsReview(val candidates: List<Candidate>) : MatchResult

    /** Not anime, or sync disabled for this series/library. */
    data object NotApplicable : MatchResult
}

enum class MatchMethod { PROVIDER_ID, ANIDB_BRIDGE, TITLE_SEARCH, MANUAL }

data class Candidate(
    val anilistMediaId: Int,
    val title: String,
    val year: Int?,
    val episodes: Int?,
    val score: Double,
)

interface MatchingPipeline {
    /**
     * Stage order (first hit wins): Room cache → AniList provider id →
     * AniDB id-bridge (bundled offline mapping DB) → scored title search →
     * multi-cour episode remap via sequel relations.
     */
    suspend fun resolve(episode: JellyfinEpisodeRef): MatchResult

    /** "Wrong match?" flow — persists a confirmed manual mapping. */
    suspend fun confirmManual(episode: JellyfinEpisodeRef, anilistMediaId: Int, episodeOffset: Int)
}

/** Per-library anime detection mode (Settings → Libraries). */
enum class AnimeDetectionMode { ALWAYS, DETECT, NEVER }
