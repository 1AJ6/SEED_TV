/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.IoDispatcher
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.jellyfin.model.ItemMappers.toMediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.RepeatMode

/** Everything the player needs to start one item. */
data class PlaybackSource(
    val item: MediaItem,
    val mediaSourceId: String,
    val playSessionId: String?,
    val url: String,
    val playMethod: PlayMethod,
    val startPositionMs: Long,
)

/**
 * Playback URL resolution + Jellyfin playstate reporting (design doc §4.3).
 * Strategy: prefer DirectPlay/DirectStream static URL — libVLC decodes nearly
 * everything — fall back to the server's transcoding URL only if forced.
 */
@Singleton
class PlaybackRepository @Inject constructor(
    private val sessionManager: SessionManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun resolve(itemId: String, fromBeginning: Boolean = false): SResult<PlaybackSource> =
        guarded { session ->
            val api = session.api
            val uuid = UUID.fromString(itemId)
            // Parallel fetch — shaves one network round-trip off time-to-first-frame.
            val (item, info) = coroutineScope {
                val itemDeferred = async {
                    api.userLibraryApi.getItem(itemId = uuid).content.toMediaItem(session)
                }
                val infoDeferred = async {
                    api.mediaInfoApi.getPostedPlaybackInfo(
                        itemId = uuid,
                        data = PlaybackInfoDto(autoOpenLiveStream = false),
                    ).content
                }
                itemDeferred.await() to infoDeferred.await()
            }
            val source = info.mediaSources.firstOrNull()
                ?: throw ApiClientException("No media sources for item $itemId")

            val direct = source.supportsDirectPlay || source.supportsDirectStream
            val token = api.accessToken
            val url: String
            val method: PlayMethod
            if (direct) {
                url = buildString {
                    append(session.baseUrl)
                    append("/Videos/").append(itemId).append("/stream")
                    append("?static=true&mediaSourceId=").append(source.id)
                    token?.let { append("&api_key=").append(it) }
                    info.playSessionId?.let { append("&playSessionId=").append(it) }
                }
                method = if (source.supportsDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.DIRECT_STREAM
            } else {
                val transcodeUrl = source.transcodingUrl
                    ?: throw ApiClientException("Item not direct-playable and no transcoding URL")
                url = session.baseUrl + transcodeUrl
                method = PlayMethod.TRANSCODE
            }

            PlaybackSource(
                item = item,
                mediaSourceId = source.id ?: itemId,
                playSessionId = info.playSessionId,
                url = url,
                playMethod = method,
                startPositionMs = if (fromBeginning) 0 else item.resumePositionMs,
            )
        }

    suspend fun reportStart(source: PlaybackSource, positionMs: Long) = report {
        it.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = UUID.fromString(source.item.id),
                mediaSourceId = source.mediaSourceId,
                playSessionId = source.playSessionId,
                positionTicks = positionMs.msToTicks(),
                canSeek = true,
                isPaused = false,
                isMuted = false,
                playMethod = source.playMethod,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportProgress(source: PlaybackSource, positionMs: Long, paused: Boolean) = report {
        it.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = UUID.fromString(source.item.id),
                mediaSourceId = source.mediaSourceId,
                playSessionId = source.playSessionId,
                positionTicks = positionMs.msToTicks(),
                canSeek = true,
                isPaused = paused,
                isMuted = false,
                playMethod = source.playMethod,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportStopped(source: PlaybackSource, positionMs: Long) = report {
        it.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = UUID.fromString(source.item.id),
                mediaSourceId = source.mediaSourceId,
                playSessionId = source.playSessionId,
                positionTicks = positionMs.msToTicks(),
                failed = false,
            ),
        )
    }

    /** Reports must never crash playback — fire, log-and-forget. Always on IO. */
    private suspend fun report(block: suspend (org.jellyfin.sdk.api.client.ApiClient) -> Unit) =
        withContext(io) {
            val session = sessionManager.current() ?: return@withContext
            runCatching { block(session.api) }
            Unit
        }

    private suspend fun <T> guarded(block: suspend (Session) -> T): SResult<T> = withContext(io) {
        val session = sessionManager.current()
            ?: return@withContext SResult.Error(AppError.UNAUTHORIZED)
        try {
            SResult.Success(block(session))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InvalidStatusException) {
            if (e.status == 401) {
                sessionManager.invalidate()
                SResult.Error(AppError.UNAUTHORIZED, e)
            } else SResult.Error(AppError.NETWORK, e)
        } catch (e: ApiClientException) {
            SResult.Error(AppError.NETWORK, e)
        } catch (e: Throwable) {
            SResult.Error(AppError.UNKNOWN, e)
        }
    }
}

/** Jellyfin ticks = 100ns units. */
fun Long.msToTicks(): Long = this * 10_000L
