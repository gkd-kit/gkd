package li.songe.gkd.priv

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.IAccessibilityServiceClient
import android.content.Context
import android.os.IBinder
import android.view.accessibility.IAccessibilityManager
import li.songe.gkd.util.AndroidTarget
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatAccessibilityManager {
    val value: IAccessibilityManager = IAccessibilityManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.ACCESSIBILITY_SERVICE),
        ),
    )

    fun registerUiTestAutomationService(
        owner: IBinder,
        client: IAccessibilityServiceClient,
        info: AccessibilityServiceInfo,
        userId: Int,
        flags: Int,
    ): Unit = if (AndroidTarget.UPSIDE_DOWN_CAKE) {
        value.registerUiTestAutomationService(owner, client, info, userId, flags)
    } else {
        value.registerUiTestAutomationService(owner, client, info, flags)
    }

    fun isUiAutomationRunning(): Boolean {
        val serviceDump = value.asBinder().dump()
        check(serviceDump.isNotBlank()) {
            "AccessibilityManagerService dump is empty"
        }
        return if (AndroidTarget.P) {
            containsModernUiAutomation(serviceDump)
        } else {
            containsLegacyUiAutomation(serviceDump)
        }
    }
}

private val uiAutomationDumpRegex = Regex("""\bUi Automation\[""")
private val legacyUserStateDumpRegex = Regex("""User state\[attributes:\{([\s\S]*?)services:\{""")
private val legacyCurrentUserRegex = Regex("""\bcurrentUser\s*=\s*true\b""")
private val legacyUiAutomationDumpRegex = Regex("""\bService\[""")

internal fun containsModernUiAutomation(dump: String): Boolean {
    return uiAutomationDumpRegex.containsMatchIn(dump)
}

internal fun containsLegacyUiAutomation(dump: String): Boolean {
    return legacyUserStateDumpRegex.findAll(dump).any { result ->
        val attributes = result.groupValues[1]
        legacyCurrentUserRegex.containsMatchIn(attributes) &&
                legacyUiAutomationDumpRegex.containsMatchIn(attributes)
    }
}
