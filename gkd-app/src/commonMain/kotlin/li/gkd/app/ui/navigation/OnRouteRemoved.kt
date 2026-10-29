package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntryDecorator
import java.util.UUID

private class RouteRemovalCallbacks {
    val callbacks = mutableMapOf<String, () -> Unit>()
}

private val LocalRouteRemovalCallbacks =
    staticCompositionLocalOf<RouteRemovalCallbacks> {
        error("OnRouteRemoved must be called inside GkNavigation")
    }

/**
 * Registers a callback once this entry is out of the back stack and composition. Normally this
 * follows its exit transition. Disposing the host while an already-popped entry is exiting also
 * completes its removal; entries still in the stack do not fire. Registration lasts until the entry
 * is removed, including while another route covers it. Recomposition updates the callback; leaving
 * composition alone does not invoke it.
 */
@Composable
fun OnRouteRemoved(onRemoved: () -> Unit) {
    val callbacks = LocalRouteRemovalCallbacks.current
    val registrationId = rememberSaveable { UUID.randomUUID().toString() }
    SideEffect { callbacks.callbacks[registrationId] = onRemoved }
}

@Composable
fun <T : Any> rememberRouteRemovedNavEntryDecorator(): NavEntryDecorator<T> = remember {
    val entries = mutableMapOf<Any, RouteRemovalCallbacks>()
    NavEntryDecorator<T>(
        // Navigation3 requires both removal from the back stack and exit from composition.
        onPop = { key -> entries.remove(key)?.callbacks?.values?.forEach { it() } },
    ) { entry ->
        val callbacks = entries.getOrPut(entry.contentKey) { RouteRemovalCallbacks() }
        CompositionLocalProvider(LocalRouteRemovalCallbacks provides callbacks) {
            entry.Content()
        }
    }
}
