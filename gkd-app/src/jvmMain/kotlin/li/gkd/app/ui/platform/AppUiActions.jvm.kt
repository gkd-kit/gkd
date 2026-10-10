package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.selectedSimulatedAutomator
import li.gkd.app.resources.*
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils

@Composable
actual fun privilegeAvailable() =
    DesktopRuntime.requireCurrent().simulator.settings.collectAsStateWithLifecycle().value.privilege.available

@Composable
actual fun ignoresBatteryOptimizations() =
    DesktopRuntime.requireCurrent().simulator.settings.collectAsStateWithLifecycle().value.environment().android.ignoreBatteryOptimizations

@Composable
actual fun RefreshPermissions() {
}

actual fun openAppDetails() {
    ToastUtils.show(Res.string.platform_action_unsupported.getSync())
}

actual fun openRecents() {
    ToastUtils.show(Res.string.platform_action_unsupported.getSync())
}

actual fun openA11ySettings() {
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Accessibility, true)
}

actual fun switchAutomator() {
    val service = selectedSimulatedAutomator()
    val current = DesktopRuntime.requireCurrent().simulator.settings.value.services.state(service)
    DesktopRuntime.requireCurrent().services.setEnabled(service, !current.active)
}

actual fun dynamicColorAvailable() = true
