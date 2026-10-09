package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.text.getSync
import org.jetbrains.compose.resources.StringResource

fun SimulatedService.label(): StringResource = when (this) {
    SimulatedService.Status -> Res.string.persistent_notification
    SimulatedService.Button -> Res.string.snapshot_button_label
    SimulatedService.Activity -> Res.string.activity_service_label
    SimulatedService.Event -> Res.string.event_service_label
    SimulatedService.Track -> Res.string.track_overlay
    SimulatedService.Screenshot -> Res.string.screenshot_service
    SimulatedService.Http -> Res.string.http_service_label
    SimulatedService.Accessibility -> Res.string.a11y_label
    SimulatedService.Automation -> Res.string.automation_label
}

fun ServicePhase.label(): StringResource = when (this) {
    ServicePhase.Stopped -> Res.string.simulation_stopped
    ServicePhase.AwaitingAuthorization -> Res.string.simulation_authorizing
    ServicePhase.Starting -> Res.string.simulation_starting
    ServicePhase.Running -> Res.string.simulation_running
    ServicePhase.Failed -> Res.string.simulation_failed
}

@Composable
fun simulatedServiceRunning(service: SimulatedService): Boolean =
    DesktopRuntime.requireCurrent().simulator.settings.collectAsStateWithLifecycle().value.services.running(service)

fun selectedSimulatedAutomator(): SimulatedService {
    val settings = SettingsRepository.settings.value
    val topAppId = DesktopRuntime.requireCurrent().simulator.settings.value.device.topAppId
    return if (settings.useA11y || topAppId in SettingsRepository.actualA11yScopeAppList)
        SimulatedService.Accessibility else SimulatedService.Automation
}

fun serviceLifecycleToast(service: SimulatedService, running: Boolean): String {
    val name = when (service) {
        SimulatedService.Accessibility -> return (if (running) Res.string.a11y_started else Res.string.a11y_stopped).getSync()
        SimulatedService.Automation -> return (if (running) Res.string.automation_started else Res.string.automation_stopped).getSync()
        SimulatedService.Status -> Res.string.persistent_notification
        SimulatedService.Button -> Res.string.snapshot_button_service
        SimulatedService.Activity -> Res.string.activity_record_service
        SimulatedService.Event -> Res.string.event_service_label
        SimulatedService.Track -> Res.string.track_overlay
        SimulatedService.Screenshot -> Res.string.screenshot_service
        SimulatedService.Http -> Res.string.http_service_compact_label
    }
    return (if (running) Res.string.service_started else Res.string.service_stopped).getSync(name.getSync())
}
