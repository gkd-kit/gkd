package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** UI event infrastructure; registrations live exactly as long as their composables. */
class DesktopBackDispatcher {
    private data class Handler(val enabled: () -> Boolean, val action: () -> Unit)

    private val handlers = mutableListOf<Handler>()
    fun register(enabled: () -> Boolean = { true }, handler: () -> Unit): () -> Unit {
        val entry = Handler(enabled, handler)
        handlers += entry
        return { handlers.remove(entry) }
    }

    fun dispatch(): Boolean =
        handlers.lastOrNull { it.enabled() }?.let { it.action(); true } ?: false
}

val LocalDesktopBackDispatcher =
    staticCompositionLocalOf<DesktopBackDispatcher> { error("Missing Desktop window host") }

val LocalDesktopRouteActive = staticCompositionLocalOf { true }

@Composable
fun GkDesktopBackHandler(onBack: () -> Unit) {
    val dispatcher = LocalDesktopBackDispatcher.current
    val current by rememberUpdatedState(onBack)
    val active by rememberUpdatedState(LocalDesktopRouteActive.current)
    DisposableEffect(dispatcher) {
        val remove = dispatcher.register(enabled = { active }) { current() }
        onDispose { remove() }
    }
}
