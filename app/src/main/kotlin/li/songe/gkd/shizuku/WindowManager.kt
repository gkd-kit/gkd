package li.songe.gkd.shizuku

import android.content.Context
import android.view.IWindowManager
import java.lang.reflect.InvocationTargetException

class SafeWindowManager(val value: IWindowManager) {
    companion object {
        fun newBinder() = getShizukuService(Context.WINDOW_SERVICE)?.let {
            SafeWindowManager(IWindowManager.Stub.asInterface(it))
        }
    }
}

// 反射查询实际api是否存在不依赖固定android版本判断
fun IWindowManager.freezeRotationCompat(rotation: Int, caller: String) {
    if (invokeCompat("freezeRotation", arrayOf(Int::class.javaPrimitiveType!!, String::class.java), rotation, caller)) {
        return
    }
    invokeCompat("freezeRotation", arrayOf(Int::class.javaPrimitiveType!!), rotation)
}

fun IWindowManager.thawRotationCompat(caller: String) {
    if (invokeCompat("thawRotation", arrayOf(String::class.java), caller)) {
        return
    }
    invokeCompat("thawRotation", emptyArray())
}

private fun IWindowManager.invokeCompat(
    name: String,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?,
): Boolean {
    return try {
        javaClass.getMethod(name, *parameterTypes).invoke(this, *args)
        true
    } catch (_: NoSuchMethodException) {
        false
    } catch (_: NoSuchMethodError) {
        false
    } catch (_: AbstractMethodError) {
        false
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
