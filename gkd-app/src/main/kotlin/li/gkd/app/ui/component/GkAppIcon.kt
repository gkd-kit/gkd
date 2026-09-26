package li.gkd.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import li.gkd.app.data.appinfo.AppInfoRepository

@Composable
fun GkAppIcon(
    modifier: Modifier = Modifier,
    appId: String,
    size: Dp = 32.dp,
) {
    val icon = AppInfoRepository.appIconMapFlow.collectAsStateWithLifecycle().value[appId]
    val iconModifier = modifier.size(size)
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = null,
            modifier = iconModifier
        )
    } else {
        GkIcon(
            imageVector = GkIcons.Android,
            modifier = iconModifier
        )
    }
}
