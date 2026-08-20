/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,          // Jellyfin server id
    val name: String,
    val baseUrl: String,
    val lastUsedAt: Long,
)

@Entity(
    tableName = "accounts",
    indices = [Index("serverId")],
)
data class AccountEntity(
    @PrimaryKey val id: String,          // "$serverId:$userId"
    val serverId: String,
    val userId: String,
    val userName: String,
    val tokenRef: String,                // key into EncryptedTokenStore — never the token itself
)

/** Confirmed/unconfirmed mapping of a Jellyfin series+season to an AniList entry. Design doc §6.4/§8. */
@Entity(
    tableName = "media_mapping",
    indices = [Index(value = ["serverId", "seriesId", "seasonNumber"], unique = true)],
)
data class MediaMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val seriesId: String,
    val seasonNumber: Int,
    val anilistMediaId: Int,
    val episodeOffset: Int,              // jellyfinEpisode - offset = anilist episode
    val confirmed: Boolean,
    val matchMethod: String,             // PROVIDER_ID | ANIDB_BRIDGE | TITLE_SEARCH | MANUAL
    val matchScore: Double,
    val syncEnabled: Boolean = true,
    val updatedAt: Long,
)

/** Durable AniList write queue drained by WorkManager. Design doc §6.6. */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val anilistMediaId: Int,
    val episode: Int,
    val action: String,                  // PROGRESS | STATUS | ADD
    val payloadJson: String,
    val state: String = "PENDING",       // PENDING | INFLIGHT | DONE | FAILED
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "scrobble_history")
data class ScrobbleHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesTitle: String,
    val episodeLabel: String,
    val anilistMediaId: Int,
    val episode: Int,
    val result: String,                  // SUCCESS | FAILED | SKIPPED_BACKWARDS | QUEUED
    val at: Long,
)

/** Local safety-net only; Jellyfin remains source of truth for resume positions. */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val itemId: String,
    val positionMs: Long,
    val updatedAt: Long,
)

/**
 * Per-show (or per-movie) preferred audio/subtitle choice. scopeKey =
 * "serverId:seriesId" for episodes (whole show, all seasons) or
 * "serverId:itemId" for movies. Product feedback 2026-08-17: set Japanese
 * audio + English subs on ep1 => applies to the entire series.
 */
@Entity(tableName = "track_prefs")
data class TrackPrefEntity(
    @PrimaryKey val scopeKey: String,
    val audioChoice: String?,      // language/display name, matched fuzzily against tracks
    val subtitleChoice: String?,
    val updatedAt: Long,
)

@Entity(tableName = "prefs")
data class PrefsEntity(
    @PrimaryKey val id: Int = 0,
    val themeColor: String = "Ember",
    val homeLayout: String = "Grid", // "Grid" or "List"
)
