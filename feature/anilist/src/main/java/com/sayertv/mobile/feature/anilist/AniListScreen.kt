/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.anilist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniListScreen(
    onBack: () -> Unit,
    viewModel: AniListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val mappings by viewModel.mappings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check link state whenever we come back from the browser
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            com.sayertv.mobile.core.designsystem.CompactTopBar(title = "AniList sync", onBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "account-card") {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        if (ui.linked) {
                            Text("Linked as ${ui.viewerName ?: "AniList user"}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Anime you watch is synced to your AniList automatically " +
                                    "when an episode passes 90% (or when you mark it watched).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = viewModel::unlink) { Text("Unlink account") }
                        } else {
                            Text("Link your AniList account", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "One-time setup:\n" +
                                    "1. On anilist.co → Settings → Apps → Developer, create a new API client\n" +
                                    "2. Set its redirect URL to exactly:  seedtv://anilist-callback\n" +
                                    "3. Paste the client ID below, then tap Link",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height (8.dp))
                            OutlinedTextField(
                                value = ui.clientId,
                                onValueChange = viewModel::onClientIdChange,
                                label = { Text("AniList client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    ui.authorizeUrl?.let {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                    }
                                },
                                enabled = ui.authorizeUrl != null,
                            ) { Text("Link AniList account") }
                        }
                    }
                }
            }

            if (mappings.isNotEmpty()) {
                item(key = "header-mappings") { Text("Series mappings", style = MaterialTheme.typography.titleMedium) }
                items(mappings, key = { "map-${it.id}" }) { mapping ->
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "AniList #${mapping.anilistMediaId}  (season ${mapping.seasonNumber})",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${mapping.matchMethod} · score %.2f · %s".format(
                                        mapping.matchScore,
                                        if (mapping.confirmed) "confirmed" else "auto",
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.forgetMapping(mapping.id) }) {
                                Text("Forget")
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item(key = "header-history") { Text("Sync history", style = MaterialTheme.typography.titleMedium) }
                items(history, key = { "hist-${it.id}" }) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${row.seriesTitle} — ${row.episodeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatTimestamp(row.at),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.result.replace('_', ' ').lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (row.result) {
                                "SUCCESS" -> MaterialTheme.colorScheme.primary
                                "FAILED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(at: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(at))
