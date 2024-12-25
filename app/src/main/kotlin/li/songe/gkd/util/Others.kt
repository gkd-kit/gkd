package li.songe.gkd.util

import android.content.ComponentName
import android.graphics.Bitmap
import li.songe.gkd.META
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

inline fun <T, R> Iterable<T>.mapHashCode(transform: (T) -> R): Int {
    return fold(0) { acc, t -> 31 * acc + transform(t).hashCode() }
}

private val componentNameCache by lazy { HashMap<String, ComponentName>() }

val KClass<*>.componentName
    get() = componentNameCache.getOrPut(jvmName) { ComponentName(META.appId, jvmName) }

fun Bitmap.isEmptyBitmap(): Boolean {
    // png
    repeat(width) { x ->
        repeat(height) { y ->
            if (getPixel(x, y) != 0) {
                return false
            }
        }
    }
    return true
}

class InterruptRuleMatchException() : Exception()

fun getShowActivityId(appId: String, activityId: String?) {
    if (activityId != null) {
        if (activityId.startsWith(appId) && activityId.getOrNull(appId.length) == '.') {
            activityId.substring(appId.length)
        } else {
            activityId
        }
    } else {
        null
    }
}
