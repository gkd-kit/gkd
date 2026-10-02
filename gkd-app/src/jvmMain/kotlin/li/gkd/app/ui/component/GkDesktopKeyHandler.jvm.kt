package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.lifecycle.compose.LifecycleStartEffect
import li.gkd.app.LocalDesktopKeyDispatcher
import li.gkd.app.LocalDesktopRouteActive

@Composable
actual fun GkDesktopKeyHandler(key: Key, enabled: Boolean, onKey: () -> Unit) {
    val dispatcher = LocalDesktopKeyDispatcher.current ?: return
    val currentOnKey by rememberUpdatedState(onKey)
    if (!enabled || !LocalDesktopRouteActive.current) return

    LifecycleStartEffect(dispatcher, key) {
        val remove = dispatcher.register(key) { currentOnKey() }
        onStopOrDispose { remove() }
    }
}
