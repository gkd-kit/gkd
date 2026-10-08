package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Lifecycle-bound host for native UI interactions. */
expect class UiHost

@Composable
expect fun GkBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)

@Composable
expect fun GkFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

@Composable
expect fun inputInsets(): Modifier
expect fun UiHost.hideIme(): Boolean

@Composable
expect fun UiHost.GkSystemBars(visible: Boolean)
