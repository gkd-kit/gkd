package li.gkd.app

/** Explicit commands only; lifecycle snapshots belong to SimulatorStore, never to a page. */
class DesktopServiceSimulation(
    private val store: SimulatorStore,
    private val setHttpEnabled: (Boolean) -> Unit,
    private val setStatusPreference: (Boolean) -> Unit,
) : AutoCloseable {
    fun setEnabled(service: SimulatedService, enabled: Boolean) {
        if (service == SimulatedService.Http) {
            if (enabled && store.settings.value.services.running(service)) return
            val denied = if (enabled) prerequisiteFailure(store.settings.value, service) else null
            if (denied != null) {
                store.update { it.copy(services = it.services.transition(service, ServicePhase.Failed, denied)) }
            } else setHttpEnabled(enabled)
            return
        }
        if (!enabled) {
            if (service == SimulatedService.Status) setStatusPreference(false)
            store.update { settings ->
                settings.copy(services = settings.services.transition(service, ServicePhase.Stopped, newAttempt = true).let {
                    if (service == SimulatedService.Accessibility) it.copy(a11yEnabled = false) else it
                })
            }
            return
        }
        val current = store.settings.value
        if (current.services.state(service).active) return
        val denied = prerequisiteFailure(current, service)
        if (service == SimulatedService.Status && denied == null) setStatusPreference(true)
        store.update { settings ->
            val needsAuthorization = service == SimulatedService.Screenshot || service == SimulatedService.Accessibility
            var services = settings.services.transition(
                service,
                if (needsAuthorization && denied == null) ServicePhase.AwaitingAuthorization else ServicePhase.Starting,
                newAttempt = true,
            )
            if (denied != null) {
                services = services.transition(service, ServicePhase.Failed, denied)
            } else if (needsAuthorization) {
                services = when (services.plans.getValue(service).authorization) {
                    ServiceAuthorization.Manual -> services
                    ServiceAuthorization.Cancel -> services.transition(service, ServicePhase.Stopped)
                    ServiceAuthorization.Allow -> authorized(services, service)
                }
            } else {
                services = completeStart(services, service)
            }
            settings.copy(services = services)
        }
    }

    fun event(event: ServiceEvent) = store.update { settings ->
        require(event.service != SimulatedService.Http) { "HTTP uses a real listener; use start/stop commands" }
        val current = settings.services.state(event.service)
        // Completion belongs to the request that produced it, including after stop/restart.
        if (current.attempt != event.attempt) return@update settings
        val services = when (event.type) {
            ServiceEventType.Authorized -> {
                require(current.phase == ServicePhase.AwaitingAuthorization) { "Service is not awaiting authorization" }
                val denied = prerequisiteFailure(settings, event.service)
                if (denied != null) settings.services.transition(event.service, ServicePhase.Failed, denied)
                else authorized(settings.services, event.service)
            }
            ServiceEventType.Cancelled -> {
                require(current.phase == ServicePhase.AwaitingAuthorization) { "Service is not awaiting authorization" }
                settings.services.transition(event.service, ServicePhase.Stopped, newAttempt = true)
            }
            ServiceEventType.Connected -> {
                require(current.phase == ServicePhase.Starting) { "Service is not starting" }
                val denied = prerequisiteFailure(settings, event.service)
                if (denied != null) settings.services.transition(event.service, ServicePhase.Failed, denied)
                else connected(settings.services, event.service)
            }
            ServiceEventType.Failed -> {
                require(current.active) { "Service is not active" }
                settings.services.transition(event.service, ServicePhase.Failed, event.reason, newAttempt = true)
            }
        }
        settings.copy(services = services)
    }

    fun configure(service: SimulatedService, plan: ServiceSimulationPlan) = store.update {
        require(service != SimulatedService.Http) { "HTTP uses a real listener" }
        it.copy(services = it.services.copy(plans = it.services.plans + (service to plan)))
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        if (!enabled) setEnabled(SimulatedService.Accessibility, false)
        else store.update { it.copy(services = it.services.copy(a11yEnabled = true)) }
    }

    private fun authorized(services: SimulatedServices, service: SimulatedService): SimulatedServices =
        completeStart(
            if (service == SimulatedService.Accessibility) services.copy(a11yEnabled = true) else services,
            service,
        )

    private fun completeStart(services: SimulatedServices, service: SimulatedService): SimulatedServices {
        val plan = services.plans.getValue(service)
        val starting = if (services.state(service).phase == ServicePhase.Starting) services
            else services.transition(service, ServicePhase.Starting)
        return when (plan.startResult) {
            ServiceStartResult.Success -> connected(starting, service)
            ServiceStartResult.Failure -> starting.transition(service, ServicePhase.Failed, plan.failure)
            ServiceStartResult.Manual -> starting
        }
    }

    private fun connected(services: SimulatedServices, service: SimulatedService): SimulatedServices {
        var next = services.transition(service, ServicePhase.Running)
        val other = when (service) {
            SimulatedService.Accessibility -> SimulatedService.Automation
            SimulatedService.Automation -> SimulatedService.Accessibility
            else -> null
        }
        if (other != null && next.state(other).active) next = next.transition(other, ServicePhase.Stopped, newAttempt = true)
        return next
    }

    private fun prerequisiteFailure(settings: SimulatorSettings, service: SimulatedService): String? {
        val permissions = settings.permissions
        if (service == SimulatedService.Automation) {
            if (!settings.privilege.available) return "Privilege service disconnected"
            if (settings.privilegeCapabilities()?.restricted == true) return "Privilege permissions denied"
            if (settings.prompts.automationOccupied) return "Automation occupied"
            return null
        }
        if (service == SimulatedService.Accessibility) return if (permissions.restricted) "Accessibility settings restricted" else null
        if (!permissions.notificationGranted) return "Notification permission denied"
        if (service != SimulatedService.Screenshot && permissions.restricted) return "Foreground service restricted"
        if (service in setOf(SimulatedService.Button, SimulatedService.Activity, SimulatedService.Event, SimulatedService.Track) && !permissions.overlayGranted) return "Overlay permission denied"
        if (service == SimulatedService.Http && !permissions.localNetworkGranted) return "Local network permission denied"
        return null
    }

    override fun close() = store.update { settings ->
        settings.copy(services = SimulatedService.entries.fold(settings.services) { services, service ->
            if (services.state(service).active) services.transition(service, ServicePhase.Stopped, newAttempt = true) else services
        })
    }
}
