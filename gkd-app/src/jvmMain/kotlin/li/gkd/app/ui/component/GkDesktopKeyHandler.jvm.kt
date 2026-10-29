package li.gkd.app.ui.component

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import li.gkd.app.LocalDesktopKeyDispatcher

@Composable
actual fun GkDesktopKeyHandler(key: Key, enabled: Boolean, onKey: () -> Unit) {
    val dispatcher = LocalDesktopKeyDispatcher.current ?: return
    val currentOnKey by rememberUpdatedState(onKey)
    if (
        !enabled ||
            LocalNavAnimatedContentScope.current.transition.targetState != EnterExitState.Visible
    )
        return

    LifecycleStartEffect(dispatcher, key) {
        val remove = dispatcher.register(key) { currentOnKey() }
        onStopOrDispose { remove() }
    }
}
