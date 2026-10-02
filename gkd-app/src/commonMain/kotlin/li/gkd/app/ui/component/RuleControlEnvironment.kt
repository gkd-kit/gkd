package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.launcherAppIdFlow
import li.gkd.app.settings.SettingsRepository

@Composable
fun rememberRuleControlEnvironment(): RuleControlEnvironment {
    val catalog by AppInfoRepository.state.collectAsStateWithLifecycle()
    val launcher by launcherAppIdFlow.collectAsStateWithLifecycle()
    val blocked by SettingsRepository.blockMatchAppList.collectAsStateWithLifecycle()
    val snapshot = catalog.snapshot
    return remember(snapshot, launcher, blocked) {
        RuleControlEnvironment(
            launcher,
            snapshot?.apps.orEmpty(), snapshot?.systemApps.orEmpty(), blocked
        )
    }
}
