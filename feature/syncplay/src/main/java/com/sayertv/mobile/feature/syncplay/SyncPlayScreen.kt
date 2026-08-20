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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sayertv.mobile.core.designsystem.CompactBarAction
import com.sayertv.mobile.core.designsystem.CompactTopBar
import com.sayertv.mobile.core.playback.SyncPlayCoordinator

@Composable
fun SyncPlayScreen(viewModel: SyncPlayViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopBar(
                title = "SyncPlay",
                actions = { CompactBarAction(Icons.Default.Refresh, "Refresh", viewModel::refresh) },
            )
        },
    ) { padding ->
        val group = activeGroup
        if (group == null) {
            GroupPicker(ui, viewModel, Modifier.padding(padding))
        } else {
            GroupRoom(group, chat, ui, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun GroupPicker(
    ui: SyncPlayUiState,
    viewModel: SyncPlayViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Watch together", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a room and anything you start playing will auto-start " +
                            "for everyone in it. Joining a room puts you in the waiting " +
                            "screen until the host picks something.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::createGroup, enabled = !ui.loading) {
                        Text("Create a room")
                    }
                }
            }
        }
        if (ui.loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (ui.groups.isNotEmpty()) {
            item(key = "header") { Text("Active rooms", style = MaterialTheme.typography.titleMedium) }
            items(ui.groups, key = { "group-${it.id}" }) { group ->
                Card(onClick = { viewModel.join(group) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${group.participants.size} watching: ${group.participants.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        TextButton(onClick = { viewModel.join(group) }) { Text("Join") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRoom(
    group: SyncPlayCoordinator.SyncGroup,
    chat: List<SyncPlayCoordinator.ChatMessage>,
    ui: SyncPlayUiState,
    viewModel: SyncPlayViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().imePadding()) {
        // Room header
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.name + if (viewModel.coordinator.isOwner) "  ·  you're the host" else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Watching: ${group.participants.joinToString().ifBlank { "just you" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                OutlinedButton(onClick = viewModel::leave) { Text("Leave") }
            }
        }

        // Chat
        val listState = rememberLazyListState()
        LaunchedEffect(chat.size) {
            if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chat.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Say hi! Chat is delivered through your Jellyfin server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(chat.size, key = { index -> "msg-$index-${chat[index].at}" }) { index ->
                val message = chat[index]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        color = if (message.mine) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            if (!message.mine) {
                                Text(
                                    message.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Composer
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextField( // Changed to TextField with transparent colors (Note 5)
                value = ui.draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = { Text("Message the room…") },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = viewModel::sendChat) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
