package li.gkd.app.matching

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import li.gkd.app.matching.diagnostics.MatchServiceConnection
import li.gkd.app.matching.diagnostics.MatchServiceEvent
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.input.MatchInput
import li.gkd.app.matching.query.readA11yWindow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.service.A11yService
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.util.ThreadUtils

object AndroidAutomationSessions {
    fun recordServiceEvent(
        service: AndroidAutomationSession,
        event: MatchServiceEvent,
        target: AndroidAutomationSession? = null,
    ) {
        MatchingRuntime.coordinator.diagnostics.record(
            0,
            MatchStage.Idle,
            event.name,
            serviceConnection =
                MatchServiceConnection(
                    event,
                    service.mode,
                    service.matchHost.connectionId,
                    target?.matchHost?.connectionId,
                ),
        )
    }

    private val latestServiceMode = atomic(0)
    private val connectionGeneration = atomic(0L)

    val service: AndroidAutomationSession?
        get() = uiAutomationFlow.value?.takeIf(::isEffective) ?: A11yService.instance

    fun isEffective(service: AndroidAutomationSession): Boolean =
        latestServiceMode.value == service.mode.value

    fun onA11yConnected(service: AndroidAutomationSession) {
        val engine = service.matchHost
        val generation = connectionGeneration.incrementAndGet()
        latestServiceMode.value = service.mode.value
        MatchingRuntime.coordinator.attach(service)
        MatchingRuntime.inputs.accept(
            MatchInput.ServiceChanged,
            MatchingRuntime.coordinator.policy.value,
        )
        recordServiceEvent(service, MatchServiceEvent.Connected)
        engine.onA11yConnected()
        ThreadUtils.runMainOrPost(MatchingRuntime.coordinator.policy.value.serviceHandoffMillis) {
            if (connectionGeneration.value == generation) {
                val target =
                    when (service.mode) {
                        AutomatorMode.A11y -> uiAutomationFlow.value
                        AutomatorMode.Automation -> A11yService.instance
                    }
                if (target != null) {
                    recordServiceEvent(service, MatchServiceEvent.HandoffShutdownRequested, target)
                    target.shutdown(true)
                }
            }
        }
    }

    // Keep each read bound to its selected service and update that engine's root cache.
    fun getRoot(service: AndroidAutomationSession? = this.service): AccessibilityNodeInfo? =
        service?.matchHost?.safeActiveWindow

    fun compatWindows(): List<AccessibilityWindowInfo> {
        return readA11yWindow(MatchingRuntime.coordinator.diagnostics, "Windows") {
                service?.windowInfos
            }
            .orEmpty()
    }

    fun onScreenForcedActive() {
        service?.matchHost?.onScreenForcedActive()
    }

    suspend fun screenshot(): Bitmap? = service?.screenshot()
}
