/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.feature.library.components.PosterCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenItem: (MediaItem) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // Removed auto-focus to prevent keyboard from opening automatically (Note 6)
    // LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search movies, shows, episodes…") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .focusRequester(focusRequester),
                )
            }
        },
    ) { padding ->
        val resultsToDisplay = if (ui.query.isBlank()) ui.recommended else ui.results
        val showEmptyState = ui.searched && ui.results.isEmpty() && ui.query.isNotBlank()

        when {
            ui.searching -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            showEmptyState ->
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(
                        "No results for “${ui.query}”",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                if (ui.query.isBlank() && ui.recommended.isNotEmpty()) {
                    Text(
                        "Recommended for you",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(resultsToDisplay, key = { it.id }) { item ->
                        PosterCard(item, onOpenItem, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
