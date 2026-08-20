/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sayertv.mobile.core.playback.AspectMode
import com.sayertv.mobile.core.playback.EngineState
import com.sayertv.mobile.core.playback.TrackType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

/**
 * M3 player. Tester-driven layout (2026-08-17):
 *  - Landscape by default + in-player rotate button
 *  - Transparent top bar: back · title (tap → show page) · rotate · lock
 *  - Ultra-slim bottom bar: 2dp track, outlined-dot thumb, compact controls
 *  - Gesture HUD cues for everything; double-tap zones: ⟲10s | play/pause | 10s⟳
 *  - Long-press = 2× while held · pinch = fill/fit · A-B repeat · delays · PiP
 *  - Next-episode autoplay with countdown card
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    onExit: () -> Unit,
    onOpenTitle: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Immersive + KEEP_SCREEN_ON + DEFAULT LANDSCAPE (tester note 1)
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Note 5: typing in chat auto-hides the playback controls until the
    // keyboard is dismissed.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible) viewModel.setControlsVisible(visible = false)
    }

    LaunchedEffect(ui.controlsVisible, ui.locked, ui.dialogOpen) {
        // Note 2 fix: never auto-hide while a menu/dialog is open; restore
        // controls when it closes.
        if (ui.controlsVisible && !ui.dialogOpen) {
            delay(4.seconds)
            viewModel.setControlsVisible(visible = false)
        }
    }

    LaunchedEffect(ui.finished) { if (ui.finished) onExit() }

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var playerChatVisible by remember { mutableStateOf(value = false) }
    val rootSyncGroup by viewModel.syncPlay.activeGroup.collectAsStateWithLifecycle()
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var hudLevel by remember { mutableFloatStateOf(0f) }
    var hudType by remember { mutableStateOf<String?>(null) } // "Volume" or "Brightness"
    fun showHud(text: String, level: Float? = null, type: String? = null) {
        hudText = text
        hudLevel = level ?: 0f
        hudType = type
        hudTick++
    }
    LaunchedEffect(hudTick) {
        if (hudText != null) {
            delay(900.milliseconds)
            hudText = null
            hudType = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        engine?.let { e ->
            AndroidView(
                factory = { ctx -> VLCVideoLayout(ctx).also { layout -> e.attachSurface(layout) } },
                onRelease = { e.detachSurface() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- Gesture layer (rebuilt, note 8): exactly two detectors so they
        //      can't race each other — taps, and one unified pan/zoom handler.
        //      Hold-2x removed (note 10). Transport gestures host-only (note 4).
        val restricted by viewModel.restricted.collectAsStateWithLifecycle()
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui.locked, restricted) {
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() },
                        onDoubleTap = { offset ->
                            if (!ui.locked && !restricted) {
                                when {
                                    (offset.x < (size.width / 3)) -> {
                                        viewModel.seekBy(-10_000); showHud("-10s")
                                    }
                                    (offset.x > ((size.width * 2) / 3)) -> {
                                        viewModel.seekBy(10_000); showHud("+10s")
                                    }
                                    else -> {
                                        viewModel.togglePlayPause()
                                        showHud(if (viewModel.playing.value) "Paused" else "Playing")
                                    }
                                }
                            }
                        },
                    )
                }
                .pointerInput(ui.locked) {
                    if (!ui.locked) {
                        var dragAcc = 0f
                        var zoomAcc = 1f
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            // Pinch -> aspect toggle (allowed for everyone, note 4)
                            zoomAcc *= zoom
                            if (zoomAcc > 1.25f) {
                                zoomAcc = 1f
                                viewModel.setAspect(AspectMode.FILL); showHud("Fill")
                            } else if (zoomAcc < 0.8f) {
                                zoomAcc = 1f
                                viewModel.setAspect(AspectMode.BEST_FIT); showHud("Fit")
                            }
                            // One-finger vertical pan -> Volume (Left) / Brightness (Right)
                            if ((zoom == 1f) && (abs(pan.y) > abs(pan.x))) {
                                dragAcc += pan.y
                                val step = size.height / 20f
                                if (abs(dragAcc) >= step) {
                                    val direction = if (dragAcc < 0) 1 else -1
                                    dragAcc = 0f
                                    if (centroid.x < (size.width / 2)) {
                                        // Left side -> Volume (Note 1)
                                        audioManager?.adjustStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                                            0,
                                        )
                                        val vol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                        val max = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1)
                                            .coerceAtLeast(1)
                                        val pct = vol.toFloat() / max
                                        showHud("Volume", pct, "Volume")
                                    } else {
                                        // Right side -> Brightness (Note 2)
                                        activity?.window?.let { w ->
                                            val lp = w.attributes
                                            val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                            val next = (current + (direction * 0.05f)).coerceIn(0.05f, 1f)
                                            lp.screenBrightness = next
                                            w.attributes = lp
                                            showHud("Brightness", next, "Brightness")
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        )

        // HUD visuals (Note 3 & 4)
        hudType?.let { type ->
            val alignment = if (type == "Volume") Alignment.CenterStart else Alignment.CenterEnd
            val padding = if (type == "Volume") PaddingValues(start = 64.dp) else PaddingValues(end = 64.dp)
            
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.align(alignment),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Vertical Bar (Note 3)
                    Box(
                        Modifier
                            .width(8.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(hudLevel)
                                .align(Alignment.BottomCenter)
                                .background(Color.White)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Percentage (Note 4)
                    Text(
                        "${(hudLevel * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // HUD pill (generic messages like "Fit", "Seek", etc)
        if (hudText != null && hudType == null) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    hudText!!,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        when {
            ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ui.error != null -> Text(
                "Couldn't start playback. Check your connection and try again.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        engine?.let { e ->
            val state by e.state.collectAsStateWithLifecycle()
            if (((state as? EngineState.Buffering)?.let { it.percent < 100f } == true) ||
                (state == EngineState.Opening)
            ) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            (state as? EngineState.Error)?.let { err ->
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        err.message ?: "Playback failed.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onExit) { Text("Go back") }
                }
            }
        }

        // ---- Next-episode autoplay card (M3) ----
        ui.nextEpisode?.let { next ->
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Up next in ${ui.nextCountdown ?: 0}s",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "${next.episodeNumber?.let { "E$it · " } ?: ""}${next.name}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::playNextNow) { Text("Play now") }
                        OutlinedButton(onClick = viewModel::cancelNext) { Text("Exit") }
                    }
                }
            }
        }

        // Note 4: pop-up bubble for messages arriving while the chat is closed
        val roomChat by viewModel.syncPlay.chat.collectAsStateWithLifecycle()
        var bubbleText by remember { mutableStateOf<String?>(null) }
        var seenCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(roomChat.size, playerChatVisible) {
            if (playerChatVisible) {
                seenCount = roomChat.size
                bubbleText = null
            } else if ((roomChat.size > seenCount)) {
                val last = roomChat.lastOrNull()
                if ((last != null) && (!last.mine)) bubbleText = "${last.author}: ${last.text}"
                seenCount = roomChat.size
            }
        }
        LaunchedEffect(bubbleText) {
            if (bubbleText != null) {
                delay(4.seconds)
                bubbleText = null
            }
        }
        bubbleText?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                    .padding(top = 64.dp, end = 12.dp),
            ) {
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(220.dp),
                )
            }
        }

        // Minimal transparent chat overlay (note 5)
        if (playerChatVisible && (rootSyncGroup != null)) {
            PlayerChatOverlay(
                viewModel = viewModel,
                onHide = { playerChatVisible = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)   // static top-right, BELOW rotate/lock (note 2)
                    .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                    .padding(top = 64.dp, end = 8.dp),
            )
        }

        if (ui.controlsVisible) {
            // Note: Removed redundant windowInsetsPadding to eliminate left space/bar (Note 1)
            if (ui.locked) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = viewModel::toggleLock,
                        modifier = Modifier.align(Alignment.CenterStart).padding(16.dp),
                    ) {
                        Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    PlayerControls(
                        ui, viewModel, onExit, onOpenTitle, activity,
                        chatVisible = playerChatVisible,
                        onToggleChat = { playerChatVisible = !playerChatVisible },
                        restricted = restricted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PlayerControls(
    ui: PlayerUiState,
    viewModel: PlayerViewModel,
    onExit: () -> Unit,
    onOpenTitle: (String) -> Unit,
    activity: Activity?,
    chatVisible: Boolean,
    onToggleChat: () -> Unit,
    restricted: Boolean,
) {
    val engine = viewModel.engine.collectAsStateWithLifecycle().value ?: return
    val playing by viewModel.playing.collectAsStateWithLifecycle()
    val position by engine.position.collectAsStateWithLifecycle()
    val duration by engine.duration.collectAsStateWithLifecycle()
    val tracks by engine.tracks.collectAsStateWithLifecycle()

    var audioMenu by remember { mutableStateOf(value = false) }
    var subtitleMenu by remember { mutableStateOf(value = false) }
    var speedMenu by remember { mutableStateOf(value = false) }
    var moreMenu by remember { mutableStateOf(value = false) }
    var delayDialog by remember { mutableStateOf(value = false) }
    var syncDialog by remember { mutableStateOf(value = false) }
    val activeSyncGroup by viewModel.syncPlay.activeGroup.collectAsStateWithLifecycle()

    val anyOverlayOpen = audioMenu || subtitleMenu || speedMenu || moreMenu || delayDialog || syncDialog
    LaunchedEffect(anyOverlayOpen) { viewModel.setDialogOpen(anyOverlayOpen) }

    // ---- Top bar: FULLY TRANSPARENT (note 4). back · title(tap→page) · rotate · lock ----
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        Column(
            Modifier
                .weight(1f)
                .clickable {
                    // Note 1: tapping the title opens the media's main page
                    val item = ui.source?.item ?: return@clickable
                    onOpenTitle(item.seriesId ?: item.id)
                },
        ) {
            Text(
                ui.source?.item?.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ui.source?.item?.seriesName?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Rotate (note 1: for users with auto-rotate off)
        IconButton(
            onClick = {
                val current = activity?.requestedOrientation
                activity?.requestedOrientation =
                    if (current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
            },
        ) {
            Text("⟳", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = viewModel::toggleLock) {
            Icon(PlayerIcons.LockOpen, "Lock controls", tint = Color.White)
        }
    }

    // ---- Bottom bar: ultra-slim ----
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp),
    ) {
        var scrubbing by remember { mutableStateOf(value = false) }
        var scrubPosition by remember { mutableFloatStateOf(0f) }
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = if (scrubbing) scrubPosition else position.toFloat().coerceAtMost(duration.toFloat()),
            onValueChange = { scrubbing = true; scrubPosition = it },
            onValueChangeFinished = {
                viewModel.seekTo(scrubPosition.toLong())
                scrubbing = false
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = !restricted,   // members watch, the host drives (note 4)
            interactionSource = interaction,
            // Note 4: dot thumb with black outline, thinner track
            thumb = {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color.Black, CircleShape),
                )
            },
            track = { sliderState ->
                androidx.compose.material3.SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(2.dp),
                )
            },
            modifier = Modifier.fillMaxWidth().height(18.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!restricted) {
                IconButton(onClick = viewModel::skipPrevious, modifier = Modifier.size(34.dp)) {
                    Icon(
                        PlayerIcons.SkipPrevious,
                        "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (playing) PlayerIcons.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = viewModel::skipNext, modifier = Modifier.size(34.dp)) {
                    Icon(
                        PlayerIcons.SkipNext,
                        "Next",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "${formatTime(position)} / ${formatTime(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            if (restricted) {
                Text(
                    "Host controls playback",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (!restricted) Box {
                CompactTextButton("Audio") { audioMenu = true }
                DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                    if (tracks.audio.isEmpty()) {
                        DropdownMenuItem(text = { Text("No audio tracks") }, onClick = { audioMenu = false })
                    }
                    tracks.audio.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(if (track.id == tracks.selectedAudioId) "✓ ${track.name}" else track.name) },
                            onClick = { viewModel.selectTrack(TrackType.AUDIO, track.id); audioMenu = false },
                        )
                    }
                }
            }
            if (!restricted) Box {
                CompactTextButton("CC") { subtitleMenu = true }
                DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                    if (tracks.subtitle.isEmpty()) {
                        DropdownMenuItem(text = { Text("No subtitles") }, onClick = { subtitleMenu = false })
                    }
                    tracks.subtitle.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(if (track.id == tracks.selectedSubtitleId) "✓ ${track.name}" else track.name) },
                            onClick = { viewModel.selectTrack(TrackType.SUBTITLE, track.id); subtitleMenu = false },
                        )
                    }
                }
            }
            if (!restricted) Box {
                CompactTextButton("${ui.speed}x") { speedMenu = true }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(if (speed == ui.speed) "✓ ${speed}x" else "${speed}x") },
                            onClick = { viewModel.setSpeed(speed); speedMenu = false },
                        )
                    }
                }
            }
            CompactTextButton(ui.aspectMode.label(), onClick = viewModel::cycleAspect)
            Box {
                CompactTextButton("⋯") { moreMenu = true }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    if (!restricted) DropdownMenuItem(
                        text = { Text("Audio / subtitle delay…") },
                        onClick = { delayDialog = true; moreMenu = false },
                    )
                    if (restricted) {
                        // members: no A-B repeat / delay controls
                    } else if (ui.aMarkMs == null) {
                        DropdownMenuItem(
                            text = { Text("A-B repeat: set point A") },
                            onClick = { viewModel.setAMark(); moreMenu = false },
                        )
                    } else if (ui.bMarkMs == null) {
                        DropdownMenuItem(
                            text = { Text("A-B repeat: set point B") },
                            onClick = { viewModel.setBMark(); moreMenu = false },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("A-B repeat: clear") },
                            onClick = { viewModel.clearABMarks(); moreMenu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(if (activeSyncGroup != null) "SyncPlay: ${activeSyncGroup?.name}" else "SyncPlay — watch together…")
                        },
                        onClick = {
                            moreMenu = false
                            syncDialog = true
                            viewModel.refreshSyncGroups()
                        },
                    )
                    if (activeSyncGroup != null) {
                        DropdownMenuItem(
                            text = { Text(if (chatVisible) "Hide chat" else "Show chat") },
                            onClick = { onToggleChat(); moreMenu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Picture-in-Picture") },
                        onClick = {
                            moreMenu = false
                            runCatching {
                                activity?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build(),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (syncDialog) {
        AlertDialog(
            onDismissRequest = { syncDialog = false },
            confirmButton = { TextButton(onClick = { syncDialog = false }) { Text("Close") } },
            title = { Text("SyncPlay") },
            text = {
                Column {
                    val active = activeSyncGroup
                    if (active != null) {
                        Text(
                            "In group: ${active.name}" + if (viewModel.syncPlay.isOwner) " (you're the host)" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Watching with: ${active.participants.joinToString().ifBlank { "just you" }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (viewModel.syncPlay.isOwner) {
                                "You control playback, audio and subtitles for the whole room."
                            } else {
                                "The host controls playback and tracks — ask in chat to change something."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.leaveSyncGroup(); syncDialog = false }) {
                            Text("Leave group")
                        }
                    } else {
                        if (ui.syncBusy) {
                            CircularProgressIndicator()
                        } else if (ui.syncGroups.isEmpty()) {
                            Text("No active groups on this server.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            ui.syncGroups.forEach { group ->
                                TextButton(onClick = { viewModel.joinSyncGroup(group) }) {
                                    Text("Join: ${group.name} (${group.participants.size} watching)")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::createSyncGroup, enabled = !ui.syncBusy) {
                            Text("Start watching together")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Everyone opens the same episode, then joins the group here. " +
                                "Play, pause and seeks stay in perfect sync.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }

    if (delayDialog) {
        DelayDialog(
            audioDelayMs = ui.audioDelayMs,
            subtitleDelayMs = ui.subtitleDelayMs,
            onAudio = viewModel::adjustAudioDelay,
            onSubtitle = viewModel::adjustSubtitleDelay,
        ) {
            delayDialog = false
        }
    }
}

@Composable
private fun DelayDialog(
    audioDelayMs: Long,
    subtitleDelayMs: Long,
    onAudio: (Long) -> Unit,
    onSubtitle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Sync adjustment") },
        text = {
            Column {
                DelayRow("Audio", audioDelayMs, onAudio)
                Spacer(Modifier.height(8.dp))
                DelayRow("Subtitles", subtitleDelayMs, onSubtitle)
            }
        },
    )
}

@Composable
private fun DelayRow(label: String, valueMs: Long, onAdjust: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onAdjust(-50) }, contentPadding = PaddingValues(0.dp)) { Text("−50") }
        Text(
            "$valueMs ms",
            Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedButton(onClick = { onAdjust(50) }, contentPadding = PaddingValues(0.dp)) { Text("+50") }
    }
}

@Composable
private fun CompactTextButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun AspectMode.label(): String = when (this) {
    AspectMode.BEST_FIT -> "Fit"
    AspectMode.FILL -> "Fill"
    AspectMode.FIT_SCREEN -> "Stretch"
    AspectMode.RATIO_16_9 -> "16:9"
    AspectMode.RATIO_4_3 -> "4:3"
    else -> "Fit"
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun PlayerChatOverlay(
    viewModel: PlayerViewModel,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chat by viewModel.syncPlay.chat.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    // Note 5: fully transparent, minimal — no card, no header label.
    Column(modifier = modifier.width(260.dp), horizontalAlignment = Alignment.End) {
        val listState = rememberLazyListState()
        LaunchedEffect(chat.size) {
            if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalAlignment = Alignment.End,          // text on the right side
        ) {
            items(chat.size, key = { index -> "chat-$index-${chat[index].at}" }) { index ->
                val message = chat[index]
                Text(
                    if (message.mine) message.text else "${message.author}: ${message.text}",
                    color = if (message.mine) MaterialTheme.colorScheme.primary else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Underlined (not boxed) input
            androidx.compose.material3.TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = {
                    viewModel.syncPlay.sendChat(draft)
                    draft = ""
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) { Text("Send", style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.width(6.dp))
            // Hide: the only element WITH a background
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
            ) {
                TextButton(
                    onClick = onHide,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) { Text("Hide", color = Color.White, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
