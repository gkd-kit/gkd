package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.option.AutomatorModeOption

@Composable
actual fun AppWindow.privilegeAvailable() =
    state.simulator.settings.collectAsStateWithLifecycle().value.privilege.available

@Composable
actual fun AppWindow.ignoresBatteryOptimizations() =
    state.simulator.settings.collectAsStateWithLifecycle().value.environment().android.ignoreBatteryOptimizations

@Composable
actual fun AppWindow.RefreshPermissions() {
}

actual fun AppWindow.setStatusServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun AppWindow.requestIgnoreBatteryOptimizations() {
    state.unsupported()
}

actual fun AppWindow.openAppDetails() {
    state.unsupported()
}

actual fun AppWindow.openRecents() {
    state.unsupported()
}

actual fun AppWindow.openA11ySettings() {
    state.unsupported()
}

actual fun AppWindow.changeAutomatorMode(mode: AutomatorModeOption) {
    SettingsRepository.updateAutomatorMode(mode.value)
}

actual fun AppWindow.switchAutomator() {
    state.unsupported()
}

actual fun AppWindow.requestQueryPackages() {
    state.unsupported()
}

actual fun AppWindow.dynamicColorAvailable() = true
