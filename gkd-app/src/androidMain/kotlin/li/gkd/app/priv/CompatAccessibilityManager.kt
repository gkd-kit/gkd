package li.gkd.app.priv

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.IAccessibilityServiceClient
import android.content.Context
import android.os.IBinder
import android.view.accessibility.IAccessibilityManager
import li.gkd.app.util.AndroidTarget
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
}
