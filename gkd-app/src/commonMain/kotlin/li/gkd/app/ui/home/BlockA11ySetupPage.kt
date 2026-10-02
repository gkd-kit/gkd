package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.ignoresBatteryOptimizations
import li.gkd.app.ui.navigation.openAppDetails
import li.gkd.app.ui.navigation.openRecents
import li.gkd.app.ui.navigation.requestIgnoreBatteryOptimizations
import li.gkd.app.ui.navigation.setStatusServiceEnabled
import li.gkd.app.ui.page.BlockA11ySetupScreen

@Composable
fun BlockA11ySetupPage(window: AppWindow, onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val platform = window.dashboardPlatformState()
    BlockA11ySetupScreen(
        enabled = store.enableBlockA11yAppList,
        privilegeAvailable = platform.privilegeAvailable,
        statusRunning = platform.statusRunning,
        ignoreBatteryOptimizations = window.ignoresBatteryOptimizations(),
        onClose = onBack,
        onEnable = { SettingsRepository.updateSettings { it.copy(enableBlockA11yAppList = true) }; onBack() },
        onPrivilege = { onNavigate(PrivilegeServiceRoute) },
        onStatus = { window.setStatusServiceEnabled(true) },
        onBattery = window::requestIgnoreBatteryOptimizations,
        onAppDetails = window::openAppDetails,
        onRecents = window::openRecents,
    )
}
