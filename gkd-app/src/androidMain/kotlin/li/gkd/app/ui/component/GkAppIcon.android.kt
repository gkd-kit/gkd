package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.data.appinfo.PackageAppCatalog
import li.gkd.app.ui.component.GkAppIcon as SharedGkAppIcon

@Composable
fun GkAppIcon(
    modifier: Modifier = Modifier,
    appId: String,
    size: Dp = 32.dp,
) {
    val catalog = AppInfoRepository.state.collectAsStateWithLifecycle().value.snapshot
    val icon = PackageAppCatalog.icon(catalog?.apps?.get(appId))
    SharedGkAppIcon(
        painter = icon?.let { rememberDrawablePainter(it) },
        modifier = modifier,
        size = size,
    )
}
