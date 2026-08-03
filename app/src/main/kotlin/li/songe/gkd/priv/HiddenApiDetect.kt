package li.songe.gkd.priv

import li.songe.gkd.util.LogUtils
import java.lang.reflect.Method
import kotlin.reflect.KClass

object HiddenApiDetect {
    fun detectHiddenClass(className: String): Boolean {
        return try {
            Class.forName(className, false, javaClass.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }
}

fun KClass<*>.detectHiddenMethod(
    methodName: String,
    vararg signatures: Pair<Int, List<KClass<*>>>,
): Int {
    val targetClass = java
    val namedMethods = targetClass.methods.filter { it.name == methodName }
    namedMethods.forEach { method ->
        val parameterTypes = method.parameterTypes
        signatures.forEach { (value, expectedTypes) ->
            if (contentEquals(parameterTypes, expectedTypes)) {
                return value
            }
        }
    }
    if (namedMethods.isEmpty()) {
        throw NoSuchMethodException("${targetClass.name}::$methodName not found")
    }
    LogUtils.d("detectHiddenMethod", *namedMethods.map { it.simpleString() }.toTypedArray())
    throw NoSuchMethodException("${targetClass.name}::${methodName} not match")
}

fun KClass<*>.detectHiddenField(fieldName: String): Boolean {
    return try {
        java.getField(fieldName)
        true
    } catch (_: NoSuchFieldException) {
        false
    }
}

private fun contentEquals(array: Array<Class<*>>, list: List<KClass<*>>): Boolean {
    if (array.size != list.size) return false
    repeat(array.size) { i ->
        if (array[i] != list[i].java) {
            return false
        }
    }
    return true
}

private fun Method.simpleString(): String {
    return "${name}(${parameterTypes.joinToString(",") { it.name }}):${returnType.name}"
}
