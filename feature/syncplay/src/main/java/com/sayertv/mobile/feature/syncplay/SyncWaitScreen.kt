/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.syncplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

/**
 * The member WAITING ROOM (SyncPlay redesign 2026-08-17): a player-like black
 * screen where non-host members are locked until the host starts media (the
 * app then auto-navigates them into the real player) or they leave the group.
 */
@Composable
fun SyncWaitScreen(
    onLeft: () -> Unit,
    onJoinPlayback: (String) -> Unit,
    viewModel: SyncPlayViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val currentItem by viewModel.currentItem.collectAsStateWithLifecycle()

    // Group gone (left / kicked / server restart) → release the lock.
    LaunchedEffect(activeGroup) { if (activeGroup == null) onLeft() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Now Playing info (Note 1)
        ui.playingMediaItem?.let { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.primaryImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 100.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Now playing:",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        item.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val playingItem = currentItem
            if (playingItem == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Waiting for the host…",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Playback starts automatically when the host picks something.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                // Notes 3+4: session already running → offer to (re)join it.
                Text(
                    "The room is watching right now",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = { onJoinPlayback(playingItem) }) {
                    Text("Join current playback")
                }
            }
            Text(
                activeGroup?.let { "Room: ${it.name} · ${it.participants.joinToString()}" } ?: "",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { viewModel.leave(); onLeft() }) { Text("Leave group") }
        }

        // Chat panel (bottom, translucent)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Fix: safeDrawing already contains the IME inset on some devices,
                // which doubled the shift. Pad system bars and IME separately.
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                .imePadding()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp),
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(chat.size) {
                if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(chat.size, key = { index -> "wmsg-$index-${chat[index].at}" }) { index ->
                    val message = chat[index]
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "${message.author}: ${message.text}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ui.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text("Message the room…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = viewModel::sendChat) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
