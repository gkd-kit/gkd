package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.content.ComponentName
import android.view.Display
import li.songe.gkd.util.toast
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.typeOf

private var tasksFcType: Int? = null
private fun IActivityTaskManager.compatGetTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo> {
    if (tasksFcType == null) {
        for (f in this::class.declaredMemberFunctions.filter { it.name == "getTasks" }) {
            tasksFcType = when (f.valueParameters.map { it.type }) {
                listOf(typeOf<Int>()) -> 1
                listOf(typeOf<Int>(), typeOf<Boolean>(), typeOf<Boolean>()) -> 3
                listOf(typeOf<Int>(), typeOf<Boolean>(), typeOf<Boolean>(), typeOf<Int>()) -> 4
                else -> null
            }
            if (tasksFcType != null) {
                break
            }
        }
        if (tasksFcType == null) {
            tasksFcType = -1
            toast("获取 IActivityTaskManager:getTasks 签名错误")
        }
    }
    return try {
        when (tasksFcType) {
            1 -> this.getTasks(maxNum)
            3 -> this.getTasks(maxNum, false, true)
            4 -> this.getTasks(maxNum, false, true, Display.DEFAULT_DISPLAY)
            else -> emptyList()
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

// https://github.com/gkd-kit/gkd/issues/44
// java.lang.ClassNotFoundException:Didn't find class "android.app.IActivityTaskManager" on path: DexPathList
class SafeActivityTaskManager(private val value: IActivityTaskManager) {
    fun compatGetTasks(maxNum: Int = 1) = value.compatGetTasks(maxNum)
    fun getTopCpn(): ComponentName? = compatGetTasks().firstOrNull()?.topActivity

    fun registerTaskStackListener(listener: FixedTaskStackListener) {
        value.registerTaskStackListener(listener)
    }

    fun unregisterTaskStackListener(listener: FixedTaskStackListener) {
        value.unregisterTaskStackListener(listener)
    }
}
