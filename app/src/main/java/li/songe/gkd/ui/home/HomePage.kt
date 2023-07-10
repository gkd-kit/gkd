package li.songe.gkd.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.utils.LocalStateCache
import li.songe.gkd.utils.StateCache
import li.songe.gkd.utils.rememberCache

@RootNavGraph(start = true)
@Destination
@Composable
fun HomePage() {
    var tabIndex by rememberCache { mutableStateOf(0) }
    val subsStateCache = rememberCache { StateCache() }
    val settingStateCache = rememberCache { StateCache() }
    Scaffold(bottomBar = { BottomNavigationBar(tabIndex) { tabIndex = it } }, content = { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tabIndex) {
                0 -> CompositionLocalProvider(LocalStateCache provides subsStateCache) { SubscriptionManagePage() }
                1 -> CompositionLocalProvider(LocalStateCache provides settingStateCache) { SettingsPage() }
            }
        }
    })
}