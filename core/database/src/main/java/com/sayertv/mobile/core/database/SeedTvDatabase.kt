/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sayertv.mobile.core.database.dao.AccountDao
import com.sayertv.mobile.core.database.dao.MediaMappingDao
import com.sayertv.mobile.core.database.dao.PrefsDao
import com.sayertv.mobile.core.database.dao.ScrobbleHistoryDao
import com.sayertv.mobile.core.database.dao.ServerDao
import com.sayertv.mobile.core.database.dao.SyncQueueDao
import com.sayertv.mobile.core.database.dao.TrackPrefDao
import com.sayertv.mobile.core.database.entity.AccountEntity
import com.sayertv.mobile.core.database.entity.MediaMappingEntity
import com.sayertv.mobile.core.database.entity.PlaybackPositionEntity
import com.sayertv.mobile.core.database.entity.PrefsEntity
import com.sayertv.mobile.core.database.entity.ScrobbleHistoryEntity
import com.sayertv.mobile.core.database.entity.ServerEntity
import com.sayertv.mobile.core.database.entity.SyncQueueEntity
import com.sayertv.mobile.core.database.entity.TrackPrefEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        ServerEntity::class,
        AccountEntity::class,
        MediaMappingEntity::class,
        SyncQueueEntity::class,
        ScrobbleHistoryEntity::class,
        PlaybackPositionEntity::class,
        TrackPrefEntity::class,
        PrefsEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class SeedTvDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun accountDao(): AccountDao
    abstract fun mediaMappingDao(): MediaMappingDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun scrobbleHistoryDao(): ScrobbleHistoryDao
    abstract fun trackPrefDao(): TrackPrefDao
    abstract fun prefsDao(): PrefsDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SeedTvDatabase =
        Room.databaseBuilder(context, SeedTvDatabase::class.java, "seedtv.db")
            // Alpha phase: schema changes wipe local caches (mappings/prefs
            // rebuild automatically; Jellyfin stays source of truth).
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun serverDao(db: SeedTvDatabase) = db.serverDao()
    @Provides fun accountDao(db: SeedTvDatabase) = db.accountDao()
    @Provides fun mediaMappingDao(db: SeedTvDatabase) = db.mediaMappingDao()
    @Provides fun syncQueueDao(db: SeedTvDatabase) = db.syncQueueDao()
    @Provides fun scrobbleHistoryDao(db: SeedTvDatabase) = db.scrobbleHistoryDao()
    @Provides fun trackPrefDao(db: SeedTvDatabase) = db.trackPrefDao()
    @Provides fun prefsDao(db: SeedTvDatabase) = db.prefsDao()
}
