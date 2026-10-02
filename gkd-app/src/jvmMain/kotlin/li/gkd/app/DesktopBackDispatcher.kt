package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** UI event infrastructure; registrations live exactly as long as their composables. */
class DesktopBackDispatcher {
    private data class Handler(val enabled: () -> Boolean, val overlay: Boolean, val action: () -> Unit)

    private val handlers = mutableListOf<Handler>()
    val hasOverlay get() = handlers.any { it.overlay && it.enabled() }

    fun register(enabled: () -> Boolean = { true }, overlay: Boolean = false, handler: () -> Unit): () -> Unit {
        val entry = Handler(enabled, overlay, handler)
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
fun GkDesktopBackHandler(overlay: Boolean = false, onBack: () -> Unit) {
    val dispatcher = LocalDesktopBackDispatcher.current
    val current by rememberUpdatedState(onBack)
    val active by rememberUpdatedState(LocalDesktopRouteActive.current)
    DisposableEffect(dispatcher, overlay) {
        val remove = dispatcher.register(enabled = { active }, overlay = overlay) { current() }
        onDispose { remove() }
    }
}
