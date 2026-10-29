package li.gkd.app.matching.action

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import li.gkd.app.matching.diagnostics.observeActionOperation
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.service.A11yService

suspend fun performActionBack(): Boolean {
    val result =
        privilegeContextFlow.value?.let { privilege ->
            observeActionOperation("PrivilegedBack") { privilege.keyevent(KeyEvent.KEYCODE_BACK) }
        }
    if (result != null) return result
    return A11yService.instance?.let { service ->
        observeActionOperation("AccessibilityService.performGlobalAction:BACK") {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    } == true
}
