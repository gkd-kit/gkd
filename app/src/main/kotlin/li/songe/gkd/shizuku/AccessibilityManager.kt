package li.songe.gkd.shizuku

import android.content.Context
import android.view.accessibility.IAccessibilityManager

class SafeAccessibilityManager(val value: IAccessibilityManager) {
    companion object {
        fun newBinder() = getShizukuService(Context.ACCESSIBILITY_SERVICE)?.let {
            SafeAccessibilityManager(IAccessibilityManager.Stub.asInterface(it))
        }
    }

}