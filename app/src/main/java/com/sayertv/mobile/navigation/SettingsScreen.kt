/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.database.dao.PrefsDao
import com.sayertv.mobile.core.database.entity.PrefsEntity
import com.sayertv.mobile.core.jellyfin.Session
import com.sayertv.mobile.core.jellyfin.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val prefsDao: PrefsDao,
) : ViewModel() {
    val session: StateFlow<Session?> = sessionManager.session

    val prefs: StateFlow<PrefsEntity> = prefsDao.observePrefs()
        .map { it ?: PrefsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrefsEntity())

    fun updateThemeColor(color: String) {
        viewModelScope.launch {
            val current = prefsDao.getPrefs() ?: PrefsEntity()
            prefsDao.upsert(current.copy(themeColor = color))
        }
    }

    fun updateHomeLayout(layout: String) {
        viewModelScope.launch {
            val current = prefsDao.getPrefs() ?: PrefsEntity()
            prefsDao.upsert(current.copy(homeLayout = layout))
        }
    }

    fun signOut() {
        viewModelScope.launch { sessionManager.invalidate(signOut = true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAniList: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenServerPicker: () -> Unit, // Renamed for Note 3
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(topBar = { com.sayertv.mobile.core.designsystem.CompactTopBar(title = "Settings") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "appearance") {
                SettingsRow(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    title = "Appearance",
                    subtitle = "${prefs.themeColor} · ${prefs.homeLayout} layout",
                    onClick = onOpenAppearance
                )
            }

            item(key = "anilist") {
                SettingsRow(
                    icon = { Icon(AppIcons.Torii, contentDescription = null) },
                    title = "AniList sync",
                    subtitle = "Account link, mappings and sync history",
                    onClick = onOpenAniList,
                )
            }
            
            item(key = "server") {
                SettingsRow(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    title = "Change server",
                    subtitle = "Connect to a new server without logging out",
                    onClick = onOpenServerPicker
                )
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(32.dp)) }

            // User Info (Note 4)
            item(key = "user_info") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Signed in as ${session?.userName ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session?.serverName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Sign Out (Note 1)
            item(key = "signout") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = viewModel::signOut,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Sign out", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // About Section (Note 2)
            item(key = "about") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SEED TV",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Version 0.1.0-alpha24",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "libVLC 3.7.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Made by Ethan Sayer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A native Android client for Jellyfin with VLC-class playback.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    if (onClick != null) Card(onClick = onClick) { content() } else Card { content() }
}
