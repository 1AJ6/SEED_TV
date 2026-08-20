/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback

import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.ApplicationScope
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.common.ScrobbleSink
import com.sayertv.mobile.core.common.ScrobbleSnapshot
import com.sayertv.mobile.core.jellyfin.PlaybackRepository
import com.sayertv.mobile.core.jellyfin.PlaybackSource
import com.sayertv.mobile.core.jellyfin.ScrobbleSnapshotFactory
import com.sayertv.mobile.core.jellyfin.SessionManager
import com.sayertv.mobile.core.jellyfin.TrackPreferenceStore
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the lifecycle of "something is playing" (design doc §3.1):
 * engine creation, Jellyfin progress reporting (start / 10s ticks / stop).
 * M4 hooks the ScrobbleEngine here; M5 defers transport to SyncPlayCoordinator.
 */
@Singleton
class PlaybackSessionController @Inject constructor(
    private val engineProvider: Provider<LibVlcEngine>,
    private val playbackRepository: PlaybackRepository,
    private val trackPreferenceStore: TrackPreferenceStore,
    private val sessionManager: SessionManager,
    private val scrobbleSink: ScrobbleSink,
    private val snapshotFactory: ScrobbleSnapshotFactory,
    private val syncPlay: SyncPlayCoordinator,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _engine = MutableStateFlow<PlayerEngine?>(null)
    val engine: StateFlow<PlayerEngine?> = _engine.asStateFlow()

    private val _nowPlaying = MutableStateFlow<PlaybackSource?>(null)
    val nowPlaying: StateFlow<PlaybackSource?> = _nowPlaying.asStateFlow()

    private var progressJob: Job? = null
    private var trackApplyJob: Job? = null
    private var scrobbleBase: ScrobbleSnapshot? = null

    suspend fun start(itemId: String, fromBeginning: Boolean = false): SResult<PlaybackSource> {
        stop()
        val result = playbackRepository.resolve(itemId, fromBeginning)
        if (result is SResult.Success) {
            val source = result.data
            val engine = engineProvider.get()
            _engine.value = engine
            _nowPlaying.value = source
            val opened = runCatching {
                engine.open(
                    PlayableMedia(
                        url = source.url,
                        startPositionMs = source.startPositionMs,
                        // Native sync: members open paused and follow the host's
                        // STATE frames; the host plays immediately.
                        startPaused = syncPlay.isActive && !syncPlay.isOwner,
                    ),
                )
            }
            if (opened.isFailure) {
                stop()
                return SResult.Error(AppError.UNKNOWN, opened.exceptionOrNull())
            }
            playbackRepository.reportStart(source, source.startPositionMs)
            applySavedTrackPreferences(engine, source)
            // Series-enriched snapshot, computed once per item (not per tick).
            scrobbleBase = runCatching { snapshotFactory.create(source.item, 0, 0) }.getOrNull()
            progressJob = scope.launch {
                while (true) {
                    delay(PROGRESS_INTERVAL_MS)
                    val e = _engine.value ?: break
                    val s = _nowPlaying.value ?: break
                    val paused = e.state.value == EngineState.Paused
                    playbackRepository.reportProgress(s, e.position.value, paused)
                    // M4: feed the AniList scrobbler (it applies the 90% threshold)
                    scrobbleBase?.let { base ->
                        runCatching {
                            scrobbleSink.onPlaybackProgress(
                                base.copy(positionMs = e.position.value, durationMs = e.duration.value),
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    /** Also called on pause/seek so other clients update immediately (§4.3). */
    fun reportNow() {
        val e = _engine.value ?: return
        val s = _nowPlaying.value ?: return
        scope.launch {
            playbackRepository.reportProgress(s, e.position.value, e.state.value == EngineState.Paused)
        }
    }

    /**
     * Per-show track memory (product feedback 2026-08-17): when tracks appear,
     * select whichever audio/subtitle matches the saved series preference.
     */
    private fun applySavedTrackPreferences(engine: PlayerEngine, source: PlaybackSource) {
        trackApplyJob?.cancel()
        trackApplyJob = scope.launch {
            val serverId = sessionManager.current()?.serverId ?: return@launch
            val pref = trackPreferenceStore.get(trackPreferenceStore.scopeKey(serverId, source.item))
                ?: return@launch
            // Wait (max 20s) for libVLC to publish its track list.
            val tracks = withTimeoutOrNull(20_000) {
                engine.tracks.first { it.audio.isNotEmpty() || it.subtitle.isNotEmpty() }
            } ?: return@launch
            pref.audio?.let { choice ->
                tracks.audio.firstOrNull { TrackPreferenceStore.matches(choice, it.name) }
                    ?.let { engine.selectTrack(TrackType.AUDIO, it.id) }
            }
            pref.subtitle?.let { choice ->
                tracks.subtitle.firstOrNull { TrackPreferenceStore.matches(choice, it.name) }
                    ?.let { engine.selectTrack(TrackType.SUBTITLE, it.id) }
            }
        }
    }

    /** Called when the user manually picks a track mid-playback — remember it for the whole show. */
    fun persistTrackChoice(type: TrackType, trackName: String) {
        val source = _nowPlaying.value ?: return
        val serverId = sessionManager.current()?.serverId ?: return
        val key = trackPreferenceStore.scopeKey(serverId, source.item)
        scope.launch {
            when (type) {
                TrackType.AUDIO -> trackPreferenceStore.saveAudio(key, trackName)
                TrackType.SUBTITLE -> trackPreferenceStore.saveSubtitle(key, trackName)
                TrackType.VIDEO -> Unit
            }
        }
    }

    fun stop() {
        trackApplyJob?.cancel()
        trackApplyJob = null
        progressJob?.cancel()
        progressJob = null
        val e = _engine.value
        val s = _nowPlaying.value
        if (e != null && s != null) {
            val position = e.position.value
            scope.launch { playbackRepository.reportStopped(s, position) }
        }
        e?.release()
        _engine.value = null
        _nowPlaying.value = null
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 10_000L
    }
}
