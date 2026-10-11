package li.gkd.app

import kotlinx.serialization.Serializable
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

@Serializable
enum class SimulatedService(
    val labelResource: StringResource,
    private val toastNameResource: StringResource = labelResource,
) {
    Status(Res.string.persistent_notification),
    Button(Res.string.snapshot_button_label, Res.string.snapshot_button_service),
    Activity(Res.string.activity_service_label, Res.string.activity_record_service),
    Event(Res.string.event_service_label),
    Track(Res.string.track_overlay),
    Screenshot(Res.string.screenshot_service),
    Http(Res.string.http_service_label, Res.string.http_service_compact_label),
    Accessibility(Res.string.a11y_label),
    Automation(Res.string.automation_label);

    fun lifecycleToast(running: Boolean): String = when (this) {
        Accessibility -> (if (running) Res.string.a11y_started else Res.string.a11y_stopped).getSync()
        Automation -> (if (running) Res.string.automation_started else Res.string.automation_stopped).getSync()
        else -> (if (running) Res.string.service_started else Res.string.service_stopped).getSync(toastNameResource.getSync())
    }
}

@Serializable
enum class ServicePhase(val labelResource: StringResource) {
    Stopped(Res.string.simulation_stopped),
    AwaitingAuthorization(Res.string.simulation_authorizing),
    Starting(Res.string.simulation_starting),
    Running(Res.string.simulation_running),
    Failed(Res.string.simulation_failed),
}

@Serializable
enum class ServiceStartResult(val labelResource: StringResource) {
    Success(Res.string.simulation_success),
    Failure(Res.string.simulation_failed),
    Manual(Res.string.simulation_manual),
}

@Serializable
enum class ServiceAuthorization(val labelResource: StringResource) {
    Allow(Res.string.action_agree),
    Cancel(Res.string.action_cancel),
    Manual(Res.string.simulation_authorization_dialog),
}

@Serializable
data class ServiceSimulationPlan(
    val startResult: ServiceStartResult = ServiceStartResult.Success,
    val authorization: ServiceAuthorization = ServiceAuthorization.Allow,
    val failure: String = "Injected startup failure",
)

@Serializable
data class SimulatedServiceState(
    val phase: ServicePhase = ServicePhase.Stopped,
    val attempt: Long = 0,
    val failure: String? = null,
) {
    val active get() = phase == ServicePhase.Running || phase == ServicePhase.Starting || phase == ServicePhase.AwaitingAuthorization
}

@Serializable
data class SimulatedServices(
    val plans: Map<SimulatedService, ServiceSimulationPlan> = SimulatedService.entries
        .filter { it != SimulatedService.Http }
        .associateWith { ServiceSimulationPlan(authorization = if (it == SimulatedService.Screenshot) ServiceAuthorization.Manual else ServiceAuthorization.Allow) },
    val runtime: Map<SimulatedService, SimulatedServiceState> = SimulatedService.entries.associateWith { SimulatedServiceState() },
    val a11yEnabled: Boolean = false,
    val partiallyDisabled: Boolean = false,
    val logs: List<String> = emptyList(),
) {
    fun state(service: SimulatedService) = runtime.getValue(service)
    fun running(service: SimulatedService) = state(service).phase == ServicePhase.Running
    val serviceEnabled get() = running(SimulatedService.Accessibility)
    val automationRunning get() = running(SimulatedService.Automation)
    val activityRunning get() = running(SimulatedService.Activity)
    val statusEnabled get() = running(SimulatedService.Status)

    fun persistent() = copy(runtime = SimulatedService.entries.associateWith { SimulatedServiceState() }, logs = emptyList())

    fun transition(service: SimulatedService, phase: ServicePhase, failure: String? = null, newAttempt: Boolean = false): SimulatedServices {
        val old = state(service)
        val next = old.copy(phase = phase, failure = failure, attempt = old.attempt + if (newAttempt) 1 else 0)
        return copy(
            runtime = runtime + (service to next),
            logs = (logs + "[simulation] $service #${next.attempt}: ${old.phase} -> $phase${failure?.let { ": $it" }.orEmpty()}").takeLast(100),
        )
    }
}

@Serializable
data class ServiceCommand(val service: SimulatedService, val enabled: Boolean)

@Serializable
enum class ServiceEventType { Authorized, Cancelled, Connected, Failed }

@Serializable
data class ServiceEvent(
    val service: SimulatedService,
    val type: ServiceEventType,
    val attempt: Long,
    val reason: String = "Injected service failure",
)
