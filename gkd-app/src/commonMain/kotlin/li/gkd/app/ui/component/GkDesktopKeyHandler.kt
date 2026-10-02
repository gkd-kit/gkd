package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key

/** Handles an unmodified desktop key on release while this composition and its route are active. */
@Composable
expect fun GkDesktopKeyHandler(
    key: Key,
    enabled: Boolean = true,
    onKey: () -> Unit,
)
