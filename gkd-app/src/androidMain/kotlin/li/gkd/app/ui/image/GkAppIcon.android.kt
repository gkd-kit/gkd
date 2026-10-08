package li.gkd.app.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import li.gkd.app.ui.component.GkAppIcon as AndroidAppIcon

@Composable
actual fun GkAppIcon(
    appId: String,
    size: Dp,
) = AndroidAppIcon(appId = appId, size = size)
