/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sayertv.mobile.core.designsystem.CompactTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel() // Reuse SettingsViewModel for prefs
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopBar(
                title = "Appearance",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Theme Color", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colors = listOf(
                        "Ember", "Ocean", "Forest", "Midnight", "Rose", 
                        "Slate", "Gold", "Mint", "Cyberpunk", "OLED",
                        "Deep Purple", "Electric Blue", "Crimson", "Emerald", "Solar"
                    )
                    colors.forEach { colorName ->
                        FilterChip(
                            selected = prefs.themeColor == colorName,
                            onClick = { viewModel.updateThemeColor(colorName) },
                            label = { Text(colorName) },
                            leadingIcon = if (prefs.themeColor == colorName) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            item {
                Text("Home Layout", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Grid", "List", "Compact").forEach { layout ->
                        FilterChip(
                            selected = prefs.homeLayout == layout,
                            onClick = { viewModel.updateHomeLayout(layout) },
                            label = { Text(layout) }
                        )
                    }
                }
            }
        }
    }
}
