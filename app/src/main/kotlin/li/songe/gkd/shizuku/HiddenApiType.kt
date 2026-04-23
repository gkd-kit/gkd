package li.songe.gkd.shizuku

import android.app.IActivityTaskManager
import android.os.IUserManager
import android.view.IWindowManager
import li.songe.gkd.util.LogUtils
import java.lang.reflect.Method

object HiddenApiType {
    // https://diff.songe.li/i/IUserManager.getUsers
    val getUsers by lazy {
        IUserManager::class.java.detectHiddenMethod(
            "getUsers",
            1 to listOf(Boolean::class.java),
            2 to listOf(Boolean::class.java, Boolean::class.java, Boolean::class.java),
        )
    }

    // https://diff.songe.li/i/IActivityTaskManager.getTasks
    val getTasks by lazy {
        IActivityTaskManager::class.java.detectHiddenMethod(
            "getTasks",
            1 to listOf(Int::class.java),
            2 to listOf(Int::class.java, Boolean::class.java, Boolean::class.java),
            3 to listOf(
                Int::class.java,
                Boolean::class.java,
                Boolean::class.java,
                Int::class.java
            ),
        )
    }

    // https://diff.songe.li/i/IWindowManager.thawRotation
    val thawRotation by lazy {
        IWindowManager::class.java.detectHiddenMethod(
            "thawRotation",
            1 to emptyList(),
            2 to listOf(String::class.java),
        )
    }

    // https://diff.songe.li/i/IWindowManager.freezeRotation
    val freezeRotation by lazy {
        IWindowManager::class.java.detectHiddenMethod(
            "freezeRotation",
            1 to listOf(Int::class.java),
            2 to listOf(Int::class.java, String::class.java),
        )
    }
}


private fun Class<*>.detectHiddenMethod(
    methodName: String,
    vararg args: Pair<Int, List<Class<*>>>,
): Int {
    val methodsVal = methods
    methodsVal.forEach { method ->
        if (method.name == methodName) {
            val types = method.parameterTypes.toList()
            args.forEach { (value, argTypes) ->
                if (types == argTypes) {
                    return value
                }
            }
        }
    }
    val result = methodsVal.filter { it.name == methodName }
    if (result.isEmpty()) {
        throw NoSuchMethodException("${name}::${methodName} not found")
    } else {
        LogUtils.d("detectHiddenMethod", *result.map { it.simpleString() }.toTypedArray())
        throw NoSuchMethodException("${name}::${methodName} not match")
    }
}

private fun Method.simpleString(): String {
    return "${name}(${parameterTypes.joinToString(",") { it.name }}):${returnType.name}"
}
