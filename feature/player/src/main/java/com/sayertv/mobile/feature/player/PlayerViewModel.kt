/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.jellyfin.LibraryRepository
import com.sayertv.mobile.core.jellyfin.PlaybackSource
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaKind
import com.sayertv.mobile.core.playback.AspectMode
import com.sayertv.mobile.core.playback.EngineState
import com.sayertv.mobile.core.playback.PlaybackSessionController
import com.sayertv.mobile.core.playback.PlayerEngine
import com.sayertv.mobile.core.playback.SyncPlayCoordinator
import com.sayertv.mobile.core.playback.TrackType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val source: PlaybackSource? = null,
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val speed: Float = 1.0f,
    val aspectMode: AspectMode = AspectMode.BEST_FIT,
    // M3
    val subtitleDelayMs: Long = 0,
    val audioDelayMs: Long = 0,
    val aMarkMs: Long? = null,
    val bMarkMs: Long? = null,
    val nextEpisode: MediaItem? = null,
    val nextCountdown: Int? = null,
    val finished: Boolean = false,
    // M5 SyncPlay
    val syncGroups: List<SyncPlayCoordinator.SyncGroup> = emptyList(),
    val syncBusy: Boolean = false,
    val dialogOpen: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlaybackSessionController,
    private val libraryRepository: LibraryRepository,
    val syncPlay: SyncPlayCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    val engine: StateFlow<PlayerEngine?> = controller.engine

    val playing: StateFlow<Boolean> = controller.engine
        .flatMapLatest { it?.playing ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Note 4: in a group, ONLY the host controls playback. Members keep
     *  aspect ratio, rotate, lock and the 3-dot menu. */
    val restricted: StateFlow<Boolean> =
        combine(syncPlay.activeGroup, syncPlay.isOwnerFlow) { group, owner -> group != null && !owner }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var abLoopJob: Job? = null
    private var nextCountdownJob: Job? = null
    private var endedHandledFor: String? = null

    init {
        startPlayback(itemId)
        // End-of-media watcher → next-episode autoplay (M3)
        viewModelScope.launch {
            controller.engine
                .flatMapLatest { it?.state ?: flowOf(EngineState.Idle) }
                .collect { state -> if (state == EngineState.Ended) onEnded() }
        }
    }

    private fun startPlayback(id: String) {
        _ui.update {
            it.copy(
                loading = true, error = null, nextEpisode = null, nextCountdown = null,
                aMarkMs = null, bMarkMs = null, subtitleDelayMs = 0, audioDelayMs = 0,
                speed = 1.0f,
            )
        }
        abLoopJob?.cancel()
        nextCountdownJob?.cancel()
        viewModelScope.launch {
            when (val result = controller.start(id)) {
                is SResult.Success -> {
                    endedHandledFor = null
                    _ui.update { it.copy(loading = false, source = result.data) }
                    engine.value?.let(syncPlay::attachEngine)
                    // Host started media → announce over the NATIVE sync channel
                    // so every member auto-opens the same item.
                    if (syncPlay.isActive && syncPlay.isOwner) {
                        syncPlay.hostAnnouncePlay(result.data.item.id, result.data.startPositionMs)
                    }
                }
                is SResult.Error -> _ui.update { it.copy(loading = false, error = result.error) }
                SResult.Loading -> Unit
            }
        }
    }

    private suspend fun onEnded() {
        val source = _ui.value.source ?: return
        if (endedHandledFor == source.item.id) return
        endedHandledFor = source.item.id
        // Note 4: while in a SyncPlay group, only the host may change media —
        // non-hosts get no next-episode autoplay.
        if (syncPlay.isActive && !syncPlay.isOwner) {
            _ui.update { it.copy(finished = true) }
            return
        }
        val seriesId = source.item.seriesId
        if (source.item.kind == MediaKind.EPISODE && seriesId != null) {
            val next = (libraryRepository.nextEpisode(seriesId, source.item.id) as? SResult.Success)?.data
            if (next != null) {
                _ui.update { it.copy(nextEpisode = next, nextCountdown = 8) }
                nextCountdownJob?.cancel()
                nextCountdownJob = viewModelScope.launch {
                    for (second in 7 downTo 0) {
                        delay(1_000)
                        _ui.update { it.copy(nextCountdown = second) }
                    }
                    playNextNow()
                }
                return
            }
        }
        _ui.update { it.copy(finished = true) }
    }

    fun playNextNow() {
        val next = _ui.value.nextEpisode ?: return
        nextCountdownJob?.cancel()
        startPlayback(next.id)
    }

    fun skipNext() {
        if (restricted.value) return
        val source = _ui.value.source ?: return
        val seriesId = source.item.seriesId
        if (source.item.kind == MediaKind.EPISODE && seriesId != null) {
            viewModelScope.launch {
                val next = (libraryRepository.nextEpisode(seriesId, source.item.id) as? SResult.Success)?.data
                if (next != null) startPlayback(next.id)
            }
        }
    }

    fun skipPrevious() {
        if (restricted.value) return
        val source = _ui.value.source ?: return
        val seriesId = source.item.seriesId
        if (source.item.kind == MediaKind.EPISODE && seriesId != null) {
            viewModelScope.launch {
                val prev = (libraryRepository.previousEpisode(seriesId, source.item.id) as? SResult.Success)?.data
                if (prev != null) startPlayback(prev.id)
            }
        }
    }

    fun cancelNext() {
        nextCountdownJob?.cancel()
        _ui.update { it.copy(nextEpisode = null, nextCountdown = null, finished = true) }
    }

    fun togglePlayPause() {
        if (restricted.value) return   // host-only (note 4)
        val e = engine.value ?: return
        // Native sync: the host acts locally and broadcasts authoritative state.
        if (e.playing.value) e.pause() else e.play()
        controller.reportNow()
        if (syncPlay.isActive && syncPlay.isOwner) syncPlay.hostBroadcastState()
    }

    fun seekTo(ms: Long) {
        if (restricted.value) return   // host-only (note 4)
        engine.value?.seekTo(ms)
        controller.reportNow()
        if (syncPlay.isActive && syncPlay.isOwner) syncPlay.hostBroadcastState()
    }

    fun seekBy(deltaMs: Long) {
        val e = engine.value ?: return
        val target = (e.position.value + deltaMs).coerceIn(0, e.duration.value)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        if (restricted.value) return
        engine.value?.setRate(speed)
        _ui.update { it.copy(speed = speed) }
    }

    fun cycleAspect() {
        val next = when (_ui.value.aspectMode) {
            AspectMode.BEST_FIT -> AspectMode.FILL
            AspectMode.FILL -> AspectMode.FIT_SCREEN
            AspectMode.FIT_SCREEN -> AspectMode.RATIO_16_9
            AspectMode.RATIO_16_9 -> AspectMode.RATIO_4_3
            AspectMode.RATIO_4_3 -> AspectMode.BEST_FIT
            else -> AspectMode.BEST_FIT
        }
        setAspect(next)
    }

    fun setAspect(mode: AspectMode) {
        engine.value?.setAspectMode(mode)
        _ui.update { it.copy(aspectMode = mode) }
    }

    fun selectTrack(type: TrackType, id: String) {
        if (restricted.value) return   // members follow the host (note 9)
        val e = engine.value ?: return
        e.selectTrack(type, id)
        val name = when (type) {
            TrackType.AUDIO -> e.tracks.value.audio.firstOrNull { it.id == id }?.name
            TrackType.SUBTITLE -> e.tracks.value.subtitle.firstOrNull { it.id == id }?.name
            TrackType.VIDEO -> null
        }
        name?.let {
            controller.persistTrackChoice(type, it)
            syncPlay.broadcastTrack(type, it)   // room follows the host's choice
        }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        if (restricted.value) return
        val next = _ui.value.subtitleDelayMs + deltaMs
        engine.value?.setSubtitleDelay(next)
        _ui.update { it.copy(subtitleDelayMs = next) }
    }

    fun adjustAudioDelay(deltaMs: Long) {
        if (restricted.value) return
        val next = _ui.value.audioDelayMs + deltaMs
        engine.value?.setAudioDelay(next)
        _ui.update { it.copy(audioDelayMs = next) }
    }

    // ---- A-B repeat ----
    fun setAMark() {
        val pos = engine.value?.position?.value ?: return
        _ui.update { it.copy(aMarkMs = pos, bMarkMs = null) }
        abLoopJob?.cancel()
    }

    fun setBMark() {
        val a = _ui.value.aMarkMs ?: return
        val pos = engine.value?.position?.value ?: return
        if (pos <= a) return
        _ui.update { it.copy(bMarkMs = pos) }
        abLoopJob?.cancel()
        abLoopJob = viewModelScope.launch {
            while (true) {
                delay(400)
                val e = engine.value ?: break
                val b = _ui.value.bMarkMs ?: break
                val aMark = _ui.value.aMarkMs ?: break
                if (e.position.value >= b) e.seekTo(aMark)
            }
        }
    }

    fun clearABMarks() {
        abLoopJob?.cancel()
        _ui.update { it.copy(aMarkMs = null, bMarkMs = null) }
    }

    fun setControlsVisible(visible: Boolean) = _ui.update { it.copy(controlsVisible = visible) }
    fun setDialogOpen(open: Boolean) = _ui.update {
        it.copy(dialogOpen = open, controlsVisible = if (!open) true else it.controlsVisible)
    }
    fun toggleControls() = _ui.update { it.copy(controlsVisible = !it.controlsVisible) }
    fun toggleLock() = _ui.update { it.copy(locked = !it.locked, controlsVisible = true) }

    // ---- SyncPlay UI actions ----
    fun refreshSyncGroups() {
        viewModelScope.launch {
            _ui.update { it.copy(syncBusy = true) }
            val groups = syncPlay.listGroups()
            _ui.update { it.copy(syncGroups = groups, syncBusy = false) }
        }
    }

    fun createSyncGroup() {
        val name = _ui.value.source?.item?.let { it.seriesName ?: it.name } ?: "SayerTV group"
        viewModelScope.launch {
            _ui.update { it.copy(syncBusy = true) }
            syncPlay.createGroup(name)
            _ui.update { it.copy(syncBusy = false) }
        }
    }

    fun joinSyncGroup(group: SyncPlayCoordinator.SyncGroup) {
        viewModelScope.launch {
            _ui.update { it.copy(syncBusy = true) }
            syncPlay.joinGroup(group)
            _ui.update { it.copy(syncBusy = false) }
        }
    }

    fun leaveSyncGroup() {
        viewModelScope.launch { syncPlay.leaveGroup() }
    }

    override fun onCleared() {
        abLoopJob?.cancel()
        nextCountdownJob?.cancel()
        syncPlay.detachEngine()
        // Redesign: exiting the player does NOT leave the group anymore —
        // members return to the waiting room; leaving is explicit (⋯ menu / tab).
        controller.stop()
    }
}
