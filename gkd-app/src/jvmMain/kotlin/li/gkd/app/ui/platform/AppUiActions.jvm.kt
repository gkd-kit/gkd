package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.resources.*
import li.gkd.app.selectedSimulatedAutomator
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.ToastUtils

@Composable
actual fun privilegeAvailable() =
    DesktopRuntime.requireCurrent()
        .simulator
        .settings
        .collectAsStateWithLifecycle()
        .value
        .privilege
        .available

@Composable
actual fun ignoresBatteryOptimizations() =
    DesktopRuntime.requireCurrent()
        .simulator
        .settings
        .collectAsStateWithLifecycle()
        .value
        .environment()
        .android
        .ignoreBatteryOptimizations

@Composable actual fun RefreshPermissions() {}

actual fun openAppDetails() {
    ToastUtils.show(Res.string.platform_action_unsupported.getSync())
}

actual suspend fun openRecents() {
    ToastUtils.show(Res.string.platform_action_unsupported.getSync())
}

actual fun openA11ySettings() {
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Accessibility, true)
}

actual fun switchAutomator() {
    val runtime = DesktopRuntime.requireCurrent()
    val services = runtime.simulator.settings.value.services
    val enabled = !(services.serviceEnabled || services.automationRunning)
    SettingsRepository.updateSettings { it.copy(enableAutomator = enabled) }
    if (enabled) {
        runtime.services.setEnabled(selectedSimulatedAutomator(), true)
    } else {
        runtime.services.setEnabled(SimulatedService.Accessibility, false)
        runtime.services.setEnabled(SimulatedService.Automation, false)
    }
}

actual fun dynamicColorAvailable() = true
