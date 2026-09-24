package li.gkd.app.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.data.ActionPerformer
import li.gkd.app.data.ActionResult
import li.gkd.app.data.GkdAction
import li.gkd.app.data.RpcError
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.service.A11yService
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.runMainPost
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.SelectorTypeResult

object A11yRuntime {
    private val latestServiceMode = atomic(0)
    private val latestServiceTime = atomic(0L)

    val service: A11yCommonImpl?
        get() = uiAutomationFlow.value?.takeIf(::isEffective) ?: A11yService.instance

    fun isEffective(service: A11yCommonImpl): Boolean =
        latestServiceMode.value == service.mode.value

    fun hasOtherService(service: A11yCommonImpl): Boolean = when (service.mode) {
        AutomatorModeOption.A11yMode -> uiAutomationFlow.value != null
        AutomatorModeOption.AutomationMode -> A11yService.instance != null
    }

    fun onA11yConnected(service: A11yCommonImpl) {
        // Preserve the engine's initial view of the other service before switching modes.
        val engine = service.ruleEngine
        val serviceTime = System.currentTimeMillis()
        latestServiceMode.value = service.mode.value
        latestServiceTime.value = serviceTime
        engine.onA11yConnected()
        runMainPost(1000L) {// Coexist for 1000ms, wait for another service to stabilize
            if (latestServiceTime.value == serviceTime) {
                when (service.mode) {
                    AutomatorModeOption.A11yMode -> uiAutomationFlow.value?.shutdown(true)
                    AutomatorModeOption.AutomationMode -> A11yService.instance?.shutdown(true)
                }
            }
        }
    }

    // Keep each read bound to its selected service and update that engine's root cache.
    fun getRoot(service: A11yCommonImpl? = this.service): AccessibilityNodeInfo? =
        service?.ruleEngine?.safeActiveWindow

    fun compatWindows(): List<AccessibilityWindowInfo> {
        return try {
            service?.windowInfos
        } catch (_: Throwable) {
            null
        } ?: emptyList()
    }

    fun onScreenForcedActive() {
        service?.ruleEngine?.onScreenForcedActive()
    }

    fun performActionBack(): Boolean {
        val result = privilegeContextFlow.value?.keyevent(KeyEvent.KEYCODE_BACK)
        if (result == true) return true
        return A11yService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
    }

    suspend fun screenshot(): Bitmap? = service?.screenshot()

    suspend fun execAction(gkdAction: GkdAction): ActionResult {
        val selectorResult = Selector.compile(gkdAction.selector)
        val selector = (selectorResult as? SelectorCompileResult.Success)?.value
            ?: throw RpcError(UiStrings.selector_invalid)
        val typeResult = selector.validateType(selectorTypeModel)
        if (typeResult is SelectorTypeResult.Failure) {
            throw RpcError(UiStrings.selector_type_error(typeResult.error.message))
        }
        val service = service ?: throw RpcError(UiStrings.service_not_connected)
        val root = getRoot(service) ?: throw RpcError(UiStrings.screen_nodes_unavailable)
        val targetNode = A11yContext(
            getRoot = { getRoot(service) },
            interruptable = false,
        ).querySelfOrSelector(
            root, selector, MatchOptions(fastQuery = gkdAction.fastQuery)
        ) ?: throw RpcError(UiStrings.selector_node_not_found)
        return withContext(Dispatchers.IO) {
            ActionPerformer
                .getAction(gkdAction.action ?: ActionPerformer.None.action)
                .perform(targetNode, gkdAction)
        }
    }
}
