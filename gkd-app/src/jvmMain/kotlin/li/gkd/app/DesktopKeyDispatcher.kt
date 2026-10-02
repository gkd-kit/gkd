package li.gkd.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

class DesktopKeyDispatcher {
    private class Handler(val key: Key, val action: () -> Unit)

    private val handlers = mutableListOf<Handler>()

    fun register(key: Key, onKey: () -> Unit): () -> Unit {
        val handler = Handler(key, onKey)
        handlers += handler
        return { handlers.remove(handler) }
    }

    fun dispatch(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyUp || event.isCtrlPressed || event.isAltPressed ||
            event.isShiftPressed || event.isMetaPressed
        ) return false
        val handler = handlers.lastOrNull { it.key == event.key } ?: return false
        handler.action()
        return true
    }
}

val LocalDesktopKeyDispatcher = staticCompositionLocalOf<DesktopKeyDispatcher?> { null }
