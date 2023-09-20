package li.songe.gkd.shizuku


import android.app.IActivityTaskManager
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.composition.CanOnDestroy
import li.songe.gkd.data.DeviceInfo
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.reflect.full.declaredMemberFunctions

fun newActivityTaskManager(): IActivityTaskManager? {
    val manager = SystemServiceHelper.getSystemService("activity_task").let(::ShizukuBinderWrapper)
        .let(IActivityTaskManager.Stub::asInterface)
    try {
        // 不同手机的签名api貌似不一样
        manager.getTasks(0, false, true)
    } catch (e: NoSuchMethodError) {
        LogUtils.d(DeviceInfo.instance)
        LogUtils.d(manager::class.declaredMemberFunctions)
        ToastUtils.showShort("Shizuku获取方法签名错误,[设置-问题反馈]可反应此问题")
        return null
    } catch (e: ClassNotFoundException) {
        ToastUtils.showShort("Shizuku获取系统对象错误,Shizuku将不生效")
        return null
    }
    return manager
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