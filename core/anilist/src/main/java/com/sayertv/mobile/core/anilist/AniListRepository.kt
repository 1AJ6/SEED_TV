/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sayertv.mobile.core.common.ApplicationScope
import com.sayertv.mobile.core.database.dao.ScrobbleHistoryDao
import com.sayertv.mobile.core.database.dao.SyncQueueDao
import com.sayertv.mobile.core.database.entity.ScrobbleHistoryEntity
import com.sayertv.mobile.core.database.entity.SyncQueueEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Durable AniList write path (design doc §6.6).
 *
 * ALPHA11 FIX: syncs now drain IMMEDIATELY in-process after every enqueue
 * (instant updates + instant Sync-history feedback). WorkManager remains as
 * the offline/retry safety net — and its default initializer is now removed
 * from the manifest so the HiltWorkerFactory configuration actually applies
 * (the silent root cause of "queue never drained").
 */
@Singleton
class AniListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authStore: AniListAuthStore,
    private val queueDao: SyncQueueDao,
    private val historyDao: ScrobbleHistoryDao,
    private val processor: SyncQueueProcessor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun isLinked(): Boolean = authStore.isLinked()

    suspend fun enqueueProgress(
        anilistMediaId: Int,
        episode: Int,
        seriesTitle: String,
        episodeLabel: String,
    ) {
        queueDao.collapsePendingProgress(anilistMediaId)
        queueDao.enqueue(
            SyncQueueEntity(
                anilistMediaId = anilistMediaId,
                episode = episode,
                action = "PROGRESS",
                payloadJson = buildJsonObject {
                    put("seriesTitle", seriesTitle)
                    put("episodeLabel", episodeLabel)
                }.toString(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        drainNow()
    }

    /** Series/season marked watched → set the AniList entry to COMPLETED. */
    suspend fun enqueueComplete(anilistMediaId: Int, seriesTitle: String) {
        queueDao.collapsePendingProgress(anilistMediaId)
        queueDao.enqueue(
            SyncQueueEntity(
                anilistMediaId = anilistMediaId,
                episode = 0,
                action = "COMPLETE",
                payloadJson = buildJsonObject {
                    put("seriesTitle", seriesTitle)
                    put("episodeLabel", "Complete series")
                }.toString(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        drainNow()
    }

    suspend fun recordSkipped(seriesTitle: String, episodeLabel: String, reason: String) {
        historyDao.insert(
            ScrobbleHistoryEntity(
                seriesTitle = seriesTitle,
                episodeLabel = episodeLabel,
                anilistMediaId = 0,
                episode = 0,
                result = reason,
                at = System.currentTimeMillis(),
            ),
        )
    }

    /** Fast path: drain in-process right now; fall back to WorkManager on failure. */
    fun drainNow() {
        scope.launch {
            val fullyDrained = runCatching { processor.drain() }.getOrDefault(false)
            if (!fullyDrained) scheduleDrain()
        }
    }

    fun scheduleDrain() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AniListSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    companion object {
        const val WORK_NAME = "anilist-sync-drain"
    }
}

/** WorkManager safety net — delegates to the shared [SyncQueueProcessor]. */
@HiltWorker
class AniListSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: SyncQueueProcessor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (runCatching { processor.drain() }.getOrDefault(false)) Result.success() else Result.retry()
}
