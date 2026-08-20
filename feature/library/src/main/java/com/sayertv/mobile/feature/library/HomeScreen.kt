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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sayertv.mobile.core.jellyfin.model.LibraryView
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.feature.library.components.MediaListItem
import com.sayertv.mobile.feature.library.components.PosterCard
import com.sayertv.mobile.feature.library.components.ResumeCard

@Composable
fun HomeScreen(
    onOpenItem: (MediaItem) -> Unit,
    onOpenLibrary: (LibraryView) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            com.sayertv.mobile.core.designsystem.CompactTopBar(
                title = "", // Removed S.E.E.D TV Title (Note 2)
                actions = {
                    com.sayertv.mobile.core.designsystem.CompactBarAction(
                        Icons.Default.Refresh, "Refresh", viewModel::refresh,
                    )
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(
                    "Couldn't load your libraries. Pull the refresh icon to retry.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> HomeContent(ui, onOpenItem, onOpenLibrary, Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeContent(
    ui: HomeUiState,
    onOpenItem: (MediaItem) -> Unit,
    onOpenLibrary: (LibraryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = ui.sections ?: return
    val isCompact = ui.homeLayout == "Compact"
    val isList = ui.homeLayout == "List"
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 20.dp),
    ) {
        if (sections.libraries.isNotEmpty()) {
            item { SectionHeader("My media") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
                ) {
                    items(sections.libraries, key = { it.id }) { library ->
                        LibraryCard(library, onOpenLibrary, isCompact)
                    }
                }
            }
        }
        
        if (sections.continueWatching.isNotEmpty()) {
            item { SectionHeader("Continue watching") }
            if (isList) {
                items(sections.continueWatching, key = { "list-continue-${it.id}" }) { 
                    MediaListItem(it, onOpenItem)
                }
            } else {
                item { 
                    MediaRow(sections.continueWatching, isCompact) { item, compact -> 
                        ResumeCard(item, onOpenItem, compact = compact) 
                    } 
                }
            }
        }
        
        if (sections.nextUp.isNotEmpty()) {
            item { SectionHeader("Next up") }
            if (isList) {
                items(sections.nextUp, key = { "list-nextup-${it.id}" }) { 
                    MediaListItem(it, onOpenItem)
                }
            } else {
                item { 
                    MediaRow(sections.nextUp, isCompact) { item, compact -> 
                        ResumeCard(item, onOpenItem, compact = compact) 
                    } 
                }
            }
        }
        
        sections.latest.forEach { section ->
            item(key = "latest-${section.library.id}") {
                SectionHeader("Latest in ${section.library.name}")
            }
            if (isList) {
                items(section.items, key = { "list-latest-${section.library.id}-${it.id}" }) { 
                    MediaListItem(it, onOpenItem)
                }
            } else {
                item(key = "latest-row-${section.library.id}") {
                    MediaRow(section.items, isCompact) { item, compact -> 
                        PosterCard(item, onOpenItem, compact = compact) 
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun MediaRow(
    items: List<MediaItem>,
    compact: Boolean = false,
    card: @Composable (MediaItem, Boolean) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        items(items, key = { it.id }) { card(it, compact) }
    }
}

@Composable
private fun LibraryCard(
    library: LibraryView,
    onClick: (LibraryView) -> Unit,
    compact: Boolean = false,
) {
    val width = if (compact) 120.dp else 160.dp
    Column(Modifier.width(width).clickable { onClick(library) }) {
        Box(
            Modifier
                .width(width)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = library.imageUrl,
                contentDescription = library.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            library.name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
