package li.songe.gkd.shizuku


import android.app.IActivityTaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.composition.CanOnDestroy
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

//val activityTaskManager: IActivityTaskManager by lazy {
//    SystemServiceHelper.getSystemService("activity_task").let(::ShizukuBinderWrapper)
//        .let(IActivityTaskManager.Stub::asInterface)
//}

//val iPackageManager: IPackageManager by lazy {
//    SystemServiceHelper.getSystemService("package").let(::ShizukuBinderWrapper)
//        .let(IPackageManager.Stub::asInterface)
//}

fun newActivityTaskManager(): IActivityTaskManager {
    return SystemServiceHelper.getSystemService("activity_task").let(::ShizukuBinderWrapper)
        .let(IActivityTaskManager.Stub::asInterface)
}


fun CanOnDestroy.useShizukuAliveState(): StateFlow<Boolean> {
    val shizukuAliveFlow = MutableStateFlow(Shizuku.pingBinder())
    val receivedListener = Shizuku.OnBinderReceivedListener { shizukuAliveFlow.value = true }
    val deadListener = Shizuku.OnBinderDeadListener { shizukuAliveFlow.value = false }
    Shizuku.addBinderReceivedListener(receivedListener)
    Shizuku.addBinderDeadListener(deadListener)
    onDestroy {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
    }
    return shizukuAliveFlow
}