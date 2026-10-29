package li.gkd.app.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.logging.LogUtils
import li.songe.codeorigin.CallSite

class AppNavigator(private val scope: CoroutineScope) {
    val backStack: List<AppNavEntry>
        field = NavBackStack(AppNavEntry(HomeRoute))

    val topEntry
        get() = backStack.last()

    val topRoute
        get() = topEntry.route

    fun pop(
        @CallSite loc: String = "",
        entry: AppNavEntry = topEntry,
    ) {
        scope.launch {
            if (backStack.size > 1 && topEntry === entry) {
                val old = backStack.removeAt(backStack.lastIndex)
                LogUtils.i(
                    "pop",
                    "${old.route.javaClass.simpleName} -> ${topRoute.javaClass.simpleName}",
                    loc = loc,
                )
            }
        }
    }

    fun navigate(route: AppRoute, replaced: Boolean = false, @CallSite loc: String = "") {
        scope.launch {
            if (route != topRoute) {
                val old = topRoute
                val entry = AppNavEntry(route)
                if (replaced) backStack[backStack.lastIndex] = entry else backStack.add(entry)
                LogUtils.i(
                    "navigate",
                    "${old.javaClass.simpleName} -> ${topRoute.javaClass.simpleName}",
                    loc = loc,
                )
            }
        }
    }

    fun openWebPage(url: String) = navigate(WebViewRoute(url))

    fun openDiagnosticPage() {
        scope.launch {
            val index = backStack.indexOfLast { it.route == MatchDebugRoute }
            if (index < 0) backStack.add(AppNavEntry(MatchDebugRoute))
            else backStack.subList(index + 1, backStack.size).clear()
        }
    }

    fun popToHome() {
        scope.launch { backStack.subList(1, backStack.size).clear() }
    }

    fun reset(route: AppRoute? = null) {
        scope.launch {
            backStack.clear()
            backStack.add(AppNavEntry(HomeRoute))
            if (route != null && route != HomeRoute) backStack.add(AppNavEntry(route))
        }
    }

    fun popFromFirst(predicate: (AppRoute) -> Boolean) = scope.launch {
        val first = backStack.indexOfFirst { predicate(it.route) }
        if (first > 0) backStack.subList(first, backStack.size).clear()
    }
}
