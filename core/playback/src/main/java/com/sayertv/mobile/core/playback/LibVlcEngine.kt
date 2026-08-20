/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * libVLC 3.7.x implementation of [PlayerEngine].
 *
 * CRASH-SAFETY (alpha5 incident): libvlc_new() returns NULL — surfaced as
 * IllegalStateException("can't create LibVLC instance") — if ANY init option
 * is unrecognized. So:
 *  1. init options are a minimal, verified-valid set;
 *  2. per-stream tuning (caching, reconnect) uses per-media ':' options,
 *     which cannot kill core creation;
 *  3. creation runs through a fallback ladder (tuned → stock) and open()
 *     NEVER throws — failures become EngineState.Error for the UI.
 */
class LibVlcEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayerEngine {

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    @Volatile private var holdPausedOnce = false

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _tracks = MutableStateFlow(TrackSet())
    override val tracks: StateFlow<TrackSet> = _tracks.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    override val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    /** Tuned first, stock fallback. Never throws unless even stock init fails. */
    private fun obtainLibVlc(): LibVLC {
        libVlc?.let { return it }
        val created = try {
            // Verified-valid libVLC 3.x options only.
            LibVLC(
                context,
                arrayListOf(
                    "--audio-time-stretch",       // pitch-corrected speed (VLC parity + SyncPlay nudge)
                    "--freetype-rel-fontsize=16", // ASS/SSA sizing parity with VLC
                ),
            )
        } catch (e: Throwable) {
            // Device/option quirk → stock engine beats no playback.
            LibVLC(context)
        }
        libVlc = created
        return created
    }

    override fun open(media: PlayableMedia) {
        releasePlayer()
        try {
            val vlc = obtainLibVlc()
            val mp = MediaPlayer(vlc)
            mp.setEventListener(::onEvent)

            val vlcMedia = Media(vlc, android.net.Uri.parse(media.url)).apply {
                setHWDecoderEnabled(media.hardwareDecoding, false)
                // Per-media options: invalid values here can't kill the core.
                addOption(":network-caching=${media.networkCachingMs}")
                addOption(":http-reconnect")
                addOption(":input-fast-seek")   // trade exact-frame seeks for snappier seeking
                if (media.startPositionMs > 0) {
                    addOption(":start-time=${media.startPositionMs / 1000.0}")
                }
            }
            mp.media = vlcMedia
            vlcMedia.release()

            player = mp
            holdPausedOnce = media.startPaused
            _state.value = EngineState.Opening
            mp.play()   // play() is required to load; a pending hold pauses on first frame
        } catch (e: Throwable) {
            _state.value = EngineState.Error(e.message ?: "Could not initialize the playback engine")
        }
    }

    override fun play() { runCatching { player?.play() } }
    override fun pause() { runCatching { player?.pause() } }
    override fun seekTo(ms: Long) { runCatching { player?.time = ms } }
    override fun setRate(rate: Float) { runCatching { player?.rate = rate.coerceIn(0.25f, 4f) } }

    override fun selectTrack(type: TrackType, id: String) {
        val mp = player ?: return
        runCatching {
            when (type) {
                TrackType.AUDIO -> mp.audioTrack = id.toIntOrNull() ?: return
                TrackType.SUBTITLE -> mp.spuTrack = id.toIntOrNull() ?: return
                TrackType.VIDEO -> Unit
            }
        }
    }

    override fun setSubtitleDelay(ms: Long) { runCatching { player?.spuDelay = ms * 1000 } }
    override fun setAudioDelay(ms: Long) { runCatching { player?.audioDelay = ms * 1000 } }

    override fun setAspectMode(mode: AspectMode) {
        val mp = player ?: return
        // ALPHA8 BUG: aspectRatio/scale are no-ops with VLCVideoLayout —
        // the correct API is MediaPlayer.setVideoScale(ScaleType).
        runCatching {
            mp.videoScale = when (mode) {
                AspectMode.BEST_FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                AspectMode.FIT_SCREEN -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                AspectMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
                AspectMode.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
                AspectMode.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
                AspectMode.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            }
        }
    }

    override fun setExternalSubtitle(url: String) {
        runCatching { player?.addSlave(IMedia.Slave.Type.Subtitle, android.net.Uri.parse(url), true) }
    }

    override fun attachSurface(layout: VLCVideoLayout) {
        runCatching { player?.attachViews(layout, null, true, false) }
    }

    override fun detachSurface() { runCatching { player?.detachViews() } }

    private fun releasePlayer() {
        player?.let { mp ->
            runCatching {
                mp.setEventListener(null)
                mp.stop()
                mp.detachViews()
                mp.release()
            }
        }
        player = null
        _playing.value = false
        _state.value = EngineState.Idle
        _position.value = 0
        _duration.value = 0
        _tracks.value = TrackSet()
    }

    override fun release() {
        releasePlayer()
        runCatching { libVlc?.release() }
        libVlc = null
    }

    private fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> _state.value = EngineState.Opening
            MediaPlayer.Event.Buffering ->
                // ALPHA6 BUG: state got stuck in Buffering forever. libVLC emits
                // buffering events continuously on network streams; when the
                // buffer refills (>=100%) we must restore Playing/Paused.
                if (event.buffering < 100f) {
                    _state.value = EngineState.Buffering(event.buffering)
                } else {
                    _state.value = if (_playing.value) EngineState.Playing else EngineState.Paused
                }
            MediaPlayer.Event.Playing -> {
                if (holdPausedOnce) {
                    holdPausedOnce = false
                    runCatching { player?.pause() }
                } else {
                    _playing.value = true
                    _state.value = EngineState.Playing
                }
            }
            MediaPlayer.Event.Paused -> { _playing.value = false; _state.value = EngineState.Paused }
            MediaPlayer.Event.Stopped -> _playing.value = false
            MediaPlayer.Event.EndReached -> { _playing.value = false; _state.value = EngineState.Ended }
            MediaPlayer.Event.EncounteredError -> {
                _playing.value = false
                _state.value = EngineState.Error("Playback failed — the stream could not be decoded")
            }
            MediaPlayer.Event.TimeChanged -> _position.value = event.timeChanged
            MediaPlayer.Event.LengthChanged -> _duration.value = event.lengthChanged
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> refreshTracks()
        }
    }

    private fun refreshTracks() {
        val mp = player ?: return
        runCatching {
            _tracks.update {
                TrackSet(
                    audio = mp.audioTracks.orEmpty().map {
                        Track(it.id.toString(), TrackType.AUDIO, it.name, null)
                    },
                    subtitle = mp.spuTracks.orEmpty().map {
                        Track(it.id.toString(), TrackType.SUBTITLE, it.name, null)
                    },
                    selectedAudioId = mp.audioTrack.toString(),
                    selectedSubtitleId = mp.spuTrack.toString(),
                )
            }
        }
    }
}

private fun <T> Array<T>?.orEmpty(): List<T> = this?.toList() ?: emptyList()
