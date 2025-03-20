package li.songe.gkd.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import li.songe.gkd.data.AppInfo
import li.songe.gkd.util.appInfoCacheFlow

@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    appId: String? = null,
    appInfo: AppInfo? = null,
) {
    val icon = if (appInfo != null) {
        appInfo.icon
    } else if (appId != null) {
        appInfoCacheFlow.collectAsState().value[appId]?.icon
    } else {
        null
    }
    val iconModifier = modifier.size(32.dp)
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = null,
            modifier = iconModifier
        )
    } else {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            modifier = iconModifier
        )
    }
}
