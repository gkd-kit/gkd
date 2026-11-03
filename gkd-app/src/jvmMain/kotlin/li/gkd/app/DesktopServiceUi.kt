package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.settings.SettingsRepository

@Composable
fun simulatedServiceRunning(service: SimulatedService): Boolean =
    DesktopRuntime.requireCurrent()
        .simulator
        .settings
        .collectAsStateWithLifecycle()
        .value
        .services
        .running(service)

fun selectedSimulatedAutomator(): SimulatedService {
    return if (SettingsRepository.settings.value.useA11y) SimulatedService.Accessibility
    else SimulatedService.Automation
}
