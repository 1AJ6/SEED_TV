/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.database.dao.TrackPrefDao
import com.sayertv.mobile.core.database.entity.TrackPrefEntity
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-show audio/subtitle preference (product feedback 2026-08-17):
 * choosing Japanese audio + English subs on any episode applies to the whole
 * series — every episode, every season. Movies remember per-item.
 *
 * Matching is fuzzy because choices come from two vocabularies:
 * Jellyfin stream metadata ("jpn", "Japanese AAC stereo") and libVLC track
 * names ("Japanese - [Original]"). A small language-alias table bridges them.
 */
/** Domain view of a saved preference — Room entities never cross module boundaries. */
data class TrackChoices(val audio: String?, val subtitle: String?)

@Singleton
class TrackPreferenceStore @Inject constructor(
    private val dao: TrackPrefDao,
) {
    fun scopeKey(serverId: String, item: MediaItem): String =
        "$serverId:${item.seriesId ?: item.id}"

    suspend fun get(scopeKey: String): TrackChoices? =
        runCatching { dao.get(scopeKey) }.getOrNull()
            ?.let { TrackChoices(audio = it.audioChoice, subtitle = it.subtitleChoice) }

    suspend fun saveAudio(scopeKey: String, choice: String?) = save(scopeKey) { it.copy(audioChoice = choice) }

    suspend fun saveSubtitle(scopeKey: String, choice: String?) = save(scopeKey) { it.copy(subtitleChoice = choice) }

    private suspend fun save(scopeKey: String, mutate: (TrackPrefEntity) -> TrackPrefEntity) {
        runCatching {
            val current = dao.get(scopeKey)
                ?: TrackPrefEntity(scopeKey, null, null, 0)
            dao.upsert(mutate(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    companion object {
        /** Minimal alias table for the common cases; extend as testers report gaps. */
        private val LANGUAGE_ALIASES = listOf(
            setOf("japanese", "jpn", "ja", "jp"),
            setOf("english", "eng", "en"),
            setOf("german", "deu", "ger", "de"),
            setOf("french", "fra", "fre", "fr"),
            setOf("spanish", "spa", "es"),
            setOf("portuguese", "por", "pt"),
            setOf("italian", "ita", "it"),
            setOf("korean", "kor", "ko"),
            setOf("chinese", "chi", "zho", "zh", "mandarin"),
            setOf("russian", "rus", "ru"),
            setOf("arabic", "ara", "ar"),
            setOf("hindi", "hin", "hi"),
        )

        private fun tokens(value: String): Set<String> =
            value.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 2 }.toSet()

        /**
         * True when [choice] (saved preference) refers to the same language/track
         * as [candidate] (a live track name or stream title).
         */
        fun matches(choice: String, candidate: String): Boolean {
            if (candidate.contains(choice, ignoreCase = true) ||
                choice.contains(candidate, ignoreCase = true)
            ) return true
            val a = tokens(choice)
            val b = tokens(candidate)
            if (a.intersect(b).isNotEmpty()) return true
            // Language alias bridge: "jpn" matches "Japanese", etc.
            return LANGUAGE_ALIASES.any { aliases ->
                a.any { it in aliases } && b.any { it in aliases }
            }
        }
    }
}
