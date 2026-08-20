/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sayertv.mobile.core.database.entity.AccountEntity
import com.sayertv.mobile.core.database.entity.MediaMappingEntity
import com.sayertv.mobile.core.database.entity.PrefsEntity
import com.sayertv.mobile.core.database.entity.ScrobbleHistoryEntity
import com.sayertv.mobile.core.database.entity.ServerEntity
import com.sayertv.mobile.core.database.entity.SyncQueueEntity
import com.sayertv.mobile.core.database.entity.TrackPrefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY lastUsedAt DESC")
    fun observeServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun mostRecentlyUsed(): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun byId(id: String): ServerEntity?

    @Upsert suspend fun upsert(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE serverId = :serverId")
    suspend fun byServer(serverId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Upsert suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MediaMappingDao {
    @Query("SELECT * FROM media_mapping WHERE serverId = :serverId AND seriesId = :seriesId AND seasonNumber = :season")
    suspend fun find(serverId: String, seriesId: String, season: Int): MediaMappingEntity?

    @Query("SELECT * FROM media_mapping ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MediaMappingEntity>>

    @Upsert suspend fun upsert(mapping: MediaMappingEntity)

    @Query("DELETE FROM media_mapping WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SyncQueueDao {
    /** Newer progress for the same media replaces queued older ones (duplicate-collapse, §6.6). */
    @Query("DELETE FROM sync_queue WHERE anilistMediaId = :mediaId AND action = 'PROGRESS' AND state = 'PENDING'")
    suspend fun collapsePendingProgress(mediaId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE state = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextPending(limit: Int = 10): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET state = :state, attempts = :attempts, lastError = :error WHERE id = :id")
    suspend fun updateState(id: Long, state: String, attempts: Int, error: String?)

    /** Crash recovery: items stuck INFLIGHT from a killed worker become retryable. */
    @Query("UPDATE sync_queue SET state = 'PENDING' WHERE state = 'INFLIGHT'")
    suspend fun resetInflight()
}

@Dao
interface ScrobbleHistoryDao {
    @Query("SELECT * FROM scrobble_history ORDER BY at DESC LIMIT 100")
    fun observeRecent(): Flow<List<ScrobbleHistoryEntity>>

    @Insert suspend fun insert(row: ScrobbleHistoryEntity)
}

@Dao
interface TrackPrefDao {
    @Query("SELECT * FROM track_prefs WHERE scopeKey = :scopeKey")
    suspend fun get(scopeKey: String): TrackPrefEntity?

    @Upsert suspend fun upsert(pref: TrackPrefEntity)
}

@Dao
interface PrefsDao {
    @Query("SELECT * FROM prefs WHERE id = 0")
    fun observePrefs(): Flow<PrefsEntity?>

    @Query("SELECT * FROM prefs WHERE id = 0")
    suspend fun getPrefs(): PrefsEntity?

    @Upsert suspend fun upsert(prefs: PrefsEntity)
}
