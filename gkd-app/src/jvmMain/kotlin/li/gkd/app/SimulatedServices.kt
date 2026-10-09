package li.gkd.app

import kotlinx.serialization.Serializable

@Serializable
enum class SimulatedService { Status, Button, Activity, Event, Track, Screenshot, Http, Accessibility, Automation }

@Serializable
enum class ServicePhase { Stopped, AwaitingAuthorization, Starting, Running, Failed }

@Serializable
enum class ServiceStartResult { Success, Failure, Manual }

@Serializable
enum class ServiceAuthorization { Allow, Cancel, Manual }

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
