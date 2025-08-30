package li.songe.gkd.shizuku

import android.app.IActivityManager
import android.content.ComponentName
import li.songe.gkd.util.checkExistClass

class SafeActivityManager(private val value: IActivityManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.app.IActivityManager")

        fun newBinder() = getStubService(
            "activity",
            isAvailable,
        )?.let {
            SafeActivityManager(IActivityManager.Stub.asInterface(it))
        }
    }

    fun getTopCpn(): ComponentName? = safeInvokeMethod {
        value.getTasks(1).firstOrNull()?.topActivity
    }
}