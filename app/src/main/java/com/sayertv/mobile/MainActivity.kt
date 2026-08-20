/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.anilist.AniListLinkHandler
import com.sayertv.mobile.core.database.dao.PrefsDao
import com.sayertv.mobile.core.database.entity.PrefsEntity
import com.sayertv.mobile.core.designsystem.SeedTvTheme
import com.sayertv.mobile.navigation.SeedTvNavHost
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefsDao: PrefsDao,
) : ViewModel() {
    val themeColor: StateFlow<String> = prefsDao.observePrefs()
        .map { it?.themeColor ?: "Ember" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ember")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var aniListLinkHandler: AniListLinkHandler
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAniListCallback(intent)
        setContent {
            val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
            SeedTvTheme(themeColor = themeColor) {
                SeedTvNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAniListCallback(intent)
    }

    private fun handleAniListCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!aniListLinkHandler.isCallback(uri)) return
        lifecycleScope.launch {
            val name = aniListLinkHandler.handle(uri)
            Toast.makeText(
                this@MainActivity,
                if (name != null) "AniList linked as $name" else "AniList linking failed — try again",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
