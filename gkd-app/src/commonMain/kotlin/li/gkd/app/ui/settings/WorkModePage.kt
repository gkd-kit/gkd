package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.network.AppLinks
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.home.dashboardPlatformState
import li.gkd.app.ui.navigation.A11YScopeAppListRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.RefreshPermissions
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.changeAutomatorMode
import li.gkd.app.ui.navigation.openA11ySettings
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.page.WorkModeScreen

@Composable
fun WorkModePage(window: AppWindow, onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    window.RefreshPermissions()
    val platform = window.dashboardPlatformState()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    WorkModeScreen(
        writeSecureSettings = platform.writeSecureSettings, a11yRunning = platform.a11yRunning,
        privilegeAvailable = platform.privilegeAvailable,
        privilegeCapabilities = platform.privilegeCapabilities,
        appRestrictions = platform.appRestrictions,
        automatorMode = AutomatorModeOption.objects.findOption(store.automatorMode),
        onBack = onBack,
        onA11yMode = { window.changeAutomatorMode(AutomatorModeOption.A11yMode) },
        onAutomationMode = { window.changeAutomatorMode(AutomatorModeOption.AutomationMode) },
        onEnableA11y = window::openA11ySettings,
        onHelp = { onNavigate(WebViewRoute(AppLinks.WorkModeHelp)) },
        onPrivilege = { onNavigate(PrivilegeServiceRoute) },
        appName = window.appVersion().appName,
        onScope = { onNavigate(A11YScopeAppListRoute) },
    )
}
