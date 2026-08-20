/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaKind
import com.sayertv.mobile.feature.library.components.runtimeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onPlay: (MediaItem) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onOpenSeries: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            com.sayertv.mobile.core.designsystem.CompactTopBar(
                title = ui.item?.name.orEmpty(),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.item == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Couldn't load this title.", color = MaterialTheme.colorScheme.error)
            }
            else -> DetailContent(ui, viewModel, onPlay, onOpenItem, onOpenSeries, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailContent(
    ui: ItemDetailUiState,
    viewModel: ItemDetailViewModel,
    onPlay: (MediaItem) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onOpenSeries: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = ui.item ?: return
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Backdrop
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = item.backdropImageUrl ?: item.primaryImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                item.progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    )
                }
            }
        }
        // Meta + actions
        item {
            Column(Modifier.padding(16.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineSmall)
                // Note 1: series name links to the show's main page
                if (item.seriesName != null && item.seriesId != null) {
                    Text(
                        item.seriesName!!,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenSeries(item.seriesId!!) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item.year?.let { MetaText(it.toString()) }
                    item.runtimeLabel()?.let { MetaText(it) }
                    item.officialRating?.let { MetaText(it) }
                    item.communityRating?.let { MetaText("★ %.1f".format(it)) }
                }
                if (item.genres.isNotEmpty()) {
                    MetaText(item.genres.take(4).joinToString(" · "))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.kind == MediaKind.MOVIE || item.kind == MediaKind.EPISODE) {
                        Button(onClick = { onPlay(item) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (item.isResumable) "Resume" else "Play")
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.togglePlayed(item) },
                        enabled = !ui.togglingPlayed,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.played) "Watched ✓" else "Mark watched")
                    }
                }
                // Per-show language preferences (product feedback 2026-08-17):
                // choices here apply to the whole series before playback starts.
                if (item.kind == MediaKind.MOVIE || item.kind == MediaKind.EPISODE) {
                    if (item.audioStreams.isNotEmpty() || item.subtitleStreams.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        if (item.audioStreams.isNotEmpty()) {
                            TrackPrefRow(
                                label = "Audio",
                                options = item.audioStreams.map { it.label },
                                selected = ui.audioPref,
                                onSelect = viewModel::setAudioPref,
                            )
                        }
                        if (item.subtitleStreams.isNotEmpty()) {
                            TrackPrefRow(
                                label = "Subtitles",
                                options = item.subtitleStreams.map { it.label },
                                selected = ui.subtitlePref,
                                onSelect = viewModel::setSubtitlePref,
                            )
                        }
                        Text(
                            if (item.kind == MediaKind.EPISODE) {
                                "Applies to every episode and season of this show"
                            } else {
                                "Applies whenever you play this title"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item.overview?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // Seasons + episodes (series only)
        if (ui.seasons.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.seasons, key = { it.id }) { season ->
                        FilterChip(
                            selected = season.id == ui.selectedSeasonId,
                            onClick = { viewModel.selectSeason(season.id) },
                            label = { Text(season.name) },
                        )
                    }
                }
            }
            if (ui.episodesLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(ui.episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onClick = { onOpenItem(episode) },
                        onToggleWatched = { viewModel.togglePlayed(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EpisodeRow(
    episode: MediaItem,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = episode.primaryImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            episode.progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${episode.episodeNumber?.let { "$it. " } ?: ""}${episode.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.runtimeLabel()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onToggleWatched) {
            Icon(
                Icons.Default.Check,
                contentDescription = if (episode.played) "Mark unwatched" else "Mark watched",
                tint = if (episode.played) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackPrefRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(90.dp),
        )
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(selected ?: "Default", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(if (selected == null) "✓ Default" else "Default") },
                    onClick = { onSelect(null); open = false },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (option == selected) "✓ $option" else option) },
                        onClick = { onSelect(option); open = false },
                    )
                }
            }
        }
    }
}
