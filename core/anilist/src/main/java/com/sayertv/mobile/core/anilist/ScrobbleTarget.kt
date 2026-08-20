/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

/**
 * Scrobble target abstraction (Ethan Sayer's decision #4: keeps the door open
 * for MAL/Trakt later without touching the ScrobbleEngine).
 *
 * Implementations in v1: [AniListScrobbleTarget] (M4).
 */
interface ScrobbleTarget {
    val id: String                                      // "anilist"
    suspend fun isLinked(): Boolean
    suspend fun currentProgress(mediaId: Int): Int?     // null = not on list
    suspend fun currentStatus(mediaId: Int): ListStatus?

    /**
     * Apply a progress update. Implementations MUST be idempotent — the sync
     * queue retries on failure.
     */
    suspend fun saveProgress(update: ProgressUpdate)
}

enum class ListStatus { CURRENT, PLANNING, COMPLETED, DROPPED, PAUSED, REPEATING }

data class ProgressUpdate(
    val mediaId: Int,
    val progress: Int,
    val status: ListStatus? = null,      // set COMPLETED on final episode (§6.5)
    val setStartedAtToday: Boolean = false,
    val setCompletedAtToday: Boolean = false,
)

/**
 * Sources that may trigger a scrobble. Per Ethan Sayer's decision #3, MANUAL
 * (mark-watched from item detail / library long-press) routes through the same
 * pipeline and guard rails as playback-threshold scrobbles.
 */
enum class ScrobbleTrigger { PLAYBACK_THRESHOLD, JELLYFIN_MARKED_PLAYED, MANUAL }
