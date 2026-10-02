package li.gkd.app.util

import android.content.ComponentName
import li.gkd.app.META
import kotlin.reflect.KClass

private val componentNameCache by lazy { HashMap<String, ComponentName>() }

val KClass<*>.componentName
    get(): ComponentName {
        val className = java.name
        return componentNameCache.getOrPut(className) { ComponentName(META.appId, className) }
    }
