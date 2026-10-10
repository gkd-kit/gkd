package li.gkd.app.ui.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import li.gkd.app.ui.platform.UiHost

/** Bottom padding needed after accounting for the host's existing window insets. */
@Composable
expect fun UiHost.snapshotShareCornerPadding(): Modifier

