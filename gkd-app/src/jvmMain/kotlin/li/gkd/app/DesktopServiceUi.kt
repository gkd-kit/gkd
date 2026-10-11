package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.settings.SettingsRepository

@Composable
fun simulatedServiceRunning(service: SimulatedService): Boolean =
    DesktopRuntime.requireCurrent().simulator.settings.collectAsStateWithLifecycle().value.services.running(service)

fun selectedSimulatedAutomator(): SimulatedService {
    val settings = SettingsRepository.settings.value
    val topAppId = DesktopRuntime.requireCurrent().simulator.settings.value.device.topAppId
    return if (settings.useA11y || topAppId in SettingsRepository.actualA11yScopeAppList)
        SimulatedService.Accessibility else SimulatedService.Automation
}
