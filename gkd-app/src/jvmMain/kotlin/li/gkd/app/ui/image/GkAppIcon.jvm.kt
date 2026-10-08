package li.gkd.app.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import li.gkd.app.DesktopRuntime
import li.gkd.app.ui.component.GkAppIcon

@Composable
actual fun GkAppIcon(
    appId: String,
    size: Dp,
) = GkAppIcon(DesktopRuntime.requireCurrent().appIcon(appId), size = size)
