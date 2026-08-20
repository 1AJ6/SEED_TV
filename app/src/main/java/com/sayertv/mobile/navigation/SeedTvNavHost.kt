/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.navArgument
import android.app.Application
import com.sayertv.mobile.CrashLog
import com.sayertv.mobile.core.jellyfin.Session
import com.sayertv.mobile.core.jellyfin.SessionManager

import com.sayertv.mobile.feature.library.HomeScreen
import com.sayertv.mobile.feature.library.ItemDetailScreen
import com.sayertv.mobile.feature.library.LibraryGridScreen
import com.sayertv.mobile.feature.library.SearchScreen
import com.sayertv.mobile.feature.anilist.AniListScreen
import com.sayertv.mobile.feature.onboarding.OnboardingScreen
import com.sayertv.mobile.core.playback.SyncPlayCoordinator
import com.sayertv.mobile.feature.syncplay.SyncPlayScreen
import com.sayertv.mobile.feature.syncplay.SyncWaitScreen
import com.sayertv.mobile.feature.player.PlayerScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library/{libraryId}?libraryName={libraryName}"
    const val ITEM = "item/{itemId}"
    const val PLAYER = "player/{itemId}"
    const val ANILIST = "anilist"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val SYNCPLAY = "syncplay"
    const val SYNC_WAIT = "syncwait"

    fun library(id: String, name: String) = "library/$id?libraryName=${Uri.encode(name)}"
    fun item(id: String) = "item/$id"
    fun player(id: String) = "player/$id"
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val application: Application,
    val syncPlay: SyncPlayCoordinator,
) : ViewModel() {
    val session: StateFlow<Session?> = sessionManager.session

    private val _restored = MutableStateFlow(value = false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    private val _crashReport = MutableStateFlow<String?>(null)
    val crashReport: StateFlow<String?> = _crashReport.asStateFlow()

    init {
        viewModelScope.launch {
            _crashReport.value = runCatching { CrashLog.pendingReport(application) }.getOrNull()
            sessionManager.restoreLast()
            _restored.value = true
        }
    }

    fun dismissCrashReport() {
        CrashLog.clear(application)
        _crashReport.value = null
    }
}

@Composable
fun SeedTvNavHost(viewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val restored by viewModel.restored.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (!restored) return

    val crashReport by viewModel.crashReport.collectAsStateWithLifecycle()
    crashReport?.let { report ->
        CrashReportDialog(report = report, onDismiss = viewModel::dismissCrashReport)
    }

    val syncGroup by viewModel.syncPlay.activeGroup.collectAsStateWithLifecycle()
    val isSyncOwner by viewModel.syncPlay.isOwnerFlow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.syncPlay.playRequests.collect { itemId ->
            navController.navigate(Routes.player(itemId)) { launchSingleTop = true }
        }
    }
    val currentRouteNow = navController.currentBackStackEntry?.destination?.route
    LaunchedEffect(syncGroup, isSyncOwner, currentRouteNow) {
        val route = navController.currentBackStackEntry?.destination?.route
        if ((syncGroup != null) && (!isSyncOwner) &&
            (route != Routes.PLAYER) && (route != Routes.SYNC_WAIT)
        ) {
            navController.navigate(Routes.SYNC_WAIT) { launchSingleTop = true }
        }
    }

    LaunchedEffect(session) {
        val target = if (session == null) Routes.ONBOARDING else Routes.HOME
        val currentRoute = navController.currentDestination?.route
        if ((currentRoute != null) && (currentRoute != target)) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomTabs = listOf(
        Triple(Routes.HOME, "Home", Icons.Default.Home),
        Triple(Routes.SEARCH, "Search", Icons.Default.Search),
        Triple(Routes.SYNCPLAY, "SyncPlay", AppIcons.WatchTogether),
        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )
    val memberLocked = (syncGroup != null) && (!isSyncOwner)
    val showBottomBar = (session != null) &&
        (currentRoute != null) &&
        (currentRoute != Routes.PLAYER) &&
        (currentRoute != Routes.SYNC_WAIT) &&
        (currentRoute != Routes.ONBOARDING) &&
        (!memberLocked)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(46.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        bottomTabs.forEach { (route, label, icon) ->
                            IconButton(
                                onClick = {
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    tint = if (currentRoute == route) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (session == null) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onComplete = { })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenItem = { item -> navController.navigate(Routes.item(item.id)) },
                    onOpenLibrary = { library ->
                        navController.navigate(Routes.library(library.id, library.name))
                    },
                )
            }
            composable(
                route = Routes.LIBRARY,
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.StringType },
                    navArgument("libraryName") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                LibraryGridScreen(
                    onOpenItem = { navController.navigate(Routes.item(it.id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ITEM,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) {
                ItemDetailScreen(
                    onPlay = { item -> navController.navigate(Routes.player(item.id)) },
                    onOpenItem = { navController.navigate(Routes.item(it.id)) },
                    onOpenSeries = { seriesId -> navController.navigate(Routes.item(seriesId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) {
                PlayerScreen(
                    onExit = { navController.popBackStack() },
                    onOpenTitle = { id ->
                        navController.navigate(Routes.item(id)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.SYNCPLAY) {
                SyncPlayScreen()
            }
            composable(Routes.SYNC_WAIT) {
                SyncWaitScreen(
                    onLeft = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    onJoinPlayback = { itemId ->
                        navController.navigate(Routes.player(itemId)) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenAniList = { navController.navigate(Routes.ANILIST) },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                    onOpenServerPicker = { navController.navigate(Routes.ONBOARDING) }
                )
            }
            composable(Routes.APPEARANCE) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ANILIST) {
                AniListScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenItem = { navController.navigate(Routes.item(it.id)) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Dismiss")
            }
        },
        title = { androidx.compose.material3.Text("Previous session crashed") },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.material3.Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
    )
}
