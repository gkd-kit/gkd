package li.gkd.app.matching

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import li.gkd.app.matching.input.MatchInput
import li.gkd.app.matching.query.readA11yWindow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.service.A11yService
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.util.ThreadUtils

object AndroidAutomationSessions {
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
        engine.onA11yConnected()
        ThreadUtils.runMainOrPost(MatchingRuntime.coordinator.policy.value.serviceHandoffMillis) {
            if (connectionGeneration.value == generation) {
                when (service.mode) {
                    AutomatorMode.A11y -> uiAutomationFlow.value?.shutdown(true)
                    AutomatorMode.Automation -> A11yService.instance?.shutdown(true)
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
