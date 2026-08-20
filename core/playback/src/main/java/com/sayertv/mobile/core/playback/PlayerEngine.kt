/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback

import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Engine abstraction (design doc §5.1). :feature:player and SyncPlayCoordinator
 * depend ONLY on this interface — no libVLC types may leak past this module.
 */
interface PlayerEngine {
    val state: StateFlow<EngineState>

    /** True between Playing and Pause/Stop events — the UI play/pause icon and
     *  toggle logic MUST use this, not `state`, because `state` can legitimately
     *  sit in Buffering while media is playing (network streams). */
    val playing: StateFlow<Boolean>
    val position: StateFlow<Long>            // ms, ~250ms ticks
    val duration: StateFlow<Long>            // ms
    val tracks: StateFlow<TrackSet>
    val chapters: StateFlow<List<Chapter>>

    fun open(media: PlayableMedia)
    fun play()
    fun pause()
    fun seekTo(ms: Long)

    /** 0.25–4.0. Also used by SyncPlay drift correction (rate nudge, §7.3). */
    fun setRate(rate: Float)

    fun selectTrack(type: TrackType, id: String)
    fun setSubtitleDelay(ms: Long)
    fun setAudioDelay(ms: Long)
    fun setAspectMode(mode: AspectMode)
    fun setExternalSubtitle(url: String)

    fun attachSurface(layout: VLCVideoLayout)
    fun detachSurface()
    fun release()
}

sealed interface EngineState {
    data object Idle : EngineState
    data object Opening : EngineState
    data class Buffering(val percent: Float) : EngineState
    data object Playing : EngineState
    data object Paused : EngineState
    data object Ended : EngineState
    data class Error(val message: String?) : EngineState
}

data class PlayableMedia(
    val url: String,
    val startPositionMs: Long = 0,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val hardwareDecoding: Boolean = true,
    val networkCachingMs: Int = 1000,
    /** SyncPlay: load and buffer, but hold paused until the group Unpause. */
    val startPaused: Boolean = false,
)

enum class TrackType { VIDEO, AUDIO, SUBTITLE }

data class Track(val id: String, val type: TrackType, val name: String, val language: String?)

data class TrackSet(
    val audio: List<Track> = emptyList(),
    val subtitle: List<Track> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
)

data class Chapter(val index: Int, val name: String?, val startMs: Long, val durationMs: Long)

enum class AspectMode { BEST_FIT, FIT_SCREEN, FILL, RATIO_16_9, RATIO_4_3, ORIGINAL }
