package li.songe.gkd.util

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController


data class StateCache(
    var list: MutableList<Any?> = mutableListOf(),
    var visitCount: Int = 0
) {
    fun assign(other: StateCache) {
        other.list = list
        other.visitCount = visitCount
    }
}

val LocalStateCache = compositionLocalOf<StateCache> { error("not default value for StateCache") }

@Suppress("UNCHECKED_CAST")
@Composable
inline fun <T> rememberCache(
    crossinline calculation: @DisallowComposableCalls () -> T
): T {
    val cache = LocalStateCache.current
    val state = remember {
        val visitCount = cache.visitCount
        cache.visitCount++
        if (cache.list.size > visitCount) {
            val value = cache.list[visitCount] as T
            value
        } else {
            val value = calculation()
            cache.list.add(value)
            value
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            cache.visitCount = 0
        }
    }
    return state
}

/**
 * 如果不在乎进程的重建,可以使用此缓存保存任意数据
 */
@Composable
fun StackCacheProvider(navController: NavHostController, content: @Composable () -> Unit) {
    val stackCaches = remember {
        Array(navController.backQueue.size) { StateCache() }.toMutableList()
    }
//   不使用 mutableStateOf 来避免多余的重组
    val currentCache = remember {
        StateCache()
    }
    DisposableEffect(Unit) {
        val listener: (NavController, NavDestination, Bundle?) -> Unit =
            { navController: NavController, _: NavDestination, _: Bundle? ->
                val realSize = navController.backQueue.size
                while (realSize != stackCaches.size) {
                    if (stackCaches.size > realSize) {
                        stackCaches.removeLast()
                    } else if (stackCaches.size < realSize) {
                        stackCaches.add(StateCache())
                    } else {
                        break
                    }
                }
                stackCaches.last().assign(currentCache)
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    CompositionLocalProvider(LocalStateCache provides currentCache, content = content)
}