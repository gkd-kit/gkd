package li.gkd.app.ui.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import li.gkd.app.ui.platform.UiHost

@Composable
actual fun UiHost.snapshotShareCornerPadding(): Modifier =
    Modifier.padding(bottom = GkSnapshotShareProgressDefaults.BottomPadding)
