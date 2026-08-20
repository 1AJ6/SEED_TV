/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import com.sayertv.mobile.core.database.dao.ScrobbleHistoryDao
import com.sayertv.mobile.core.database.dao.SyncQueueDao
import com.sayertv.mobile.core.database.entity.ScrobbleHistoryEntity
import com.sayertv.mobile.core.database.entity.SyncQueueEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The single queue-drain implementation, callable from BOTH:
 *  - the in-process fast path (immediately after an enqueue → instant syncs)
 *  - AniListSyncWorker (WorkManager retry/offline safety net)
 * A mutex guarantees the two paths never process concurrently.
 *
 * Guard rails (design doc §6.5): never move progress backwards (unless
 * REPEATING), auto-add unlisted anime as CURRENT, COMPLETED on final episode.
 */
@Singleton
class SyncQueueProcessor @Inject constructor(
    private val api: AniListApi,
    private val authStore: AniListAuthStore,
    private val queueDao: SyncQueueDao,
    private val historyDao: ScrobbleHistoryDao,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** @return true when the queue is fully drained; false if retryable items remain. */
    suspend fun drain(): Boolean = mutex.withLock {
        if (!authStore.isLinked()) return true
        queueDao.resetInflight()
        var sawRetryable = false
        while (true) {
            val batch = queueDao.nextPending()
            if (batch.isEmpty()) break
            for (item in batch) {
                queueDao.updateState(item.id, "INFLIGHT", item.attempts, null)
                try {
                    process(item)
                    queueDao.updateState(item.id, "DONE", item.attempts, null)
                } catch (e: AniListException) {
                    if (e.retryable && item.attempts < MAX_ATTEMPTS) {
                        queueDao.updateState(item.id, "PENDING", item.attempts + 1, e.message)
                        sawRetryable = true
                    } else {
                        queueDao.updateState(item.id, "FAILED", item.attempts + 1, e.message)
                        history(item, "FAILED")
                    }
                } catch (e: Exception) {
                    if (item.attempts < MAX_ATTEMPTS) {
                        queueDao.updateState(item.id, "PENDING", item.attempts + 1, e.message)
                        sawRetryable = true
                    } else {
                        queueDao.updateState(item.id, "FAILED", item.attempts + 1, e.message)
                        history(item, "FAILED")
                    }
                }
            }
            if (sawRetryable) break
        }
        !sawRetryable
    }

    private suspend fun process(item: SyncQueueEntity) {
        val current = api.entry(item.anilistMediaId)

        if (item.action == "COMPLETE") {
            // Series/season marked watched → complete the whole entry.
            if (current.listStatus == "COMPLETED") {
                history(item, "ALREADY_COMPLETE")
                return
            }
            val total = current.episodes ?: current.listProgress ?: 0
            api.saveProgress(item.anilistMediaId, total, "COMPLETED")
            history(item, "SUCCESS")
            return
        }

        val target = item.episode
        val existing = current.listProgress ?: -1
        if (existing >= target && current.listStatus != "REPEATING") {
            // Your AniList already shows this episode (or later) — never regress.
            history(item, "ALREADY_AHEAD")
            return
        }

        val isFinal = current.episodes != null && target >= current.episodes
        val status = when {
            isFinal -> "COMPLETED"
            current.listStatus == null -> "CURRENT"
            else -> null
        }
        api.saveProgress(item.anilistMediaId, target, status)
        history(item, "SUCCESS")
    }

    private suspend fun history(item: SyncQueueEntity, result: String) {
        val payload = runCatching { json.parseToJsonElement(item.payloadJson).jsonObject }.getOrNull()
        historyDao.insert(
            ScrobbleHistoryEntity(
                seriesTitle = payload?.get("seriesTitle")?.jsonPrimitive?.contentOrNull ?: "Unknown",
                episodeLabel = payload?.get("episodeLabel")?.jsonPrimitive?.contentOrNull ?: "",
                anilistMediaId = item.anilistMediaId,
                episode = item.episode,
                result = result,
                at = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
