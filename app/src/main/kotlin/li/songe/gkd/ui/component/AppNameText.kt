package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.AppInfo
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Composable
fun AppNameText(
    appId: String? = null,
    appInfo: AppInfo? = null,
    fallbackName: String? = null,
) {
    val info = appInfo ?: appInfoCacheFlow.collectAsState().value[appId]
    if (info?.isSystem == true) {
        val style = LocalTextStyle.current
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier
                    .clickable(onClick = throttle(fn = { toast("${info.name} 是一个系统应用") }))
                    .size(style.fontSize.value.dp)
            )
            Text(
                text = info.name,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Text(
            text = info?.name ?: fallbackName ?: appId ?: error("appId is required"),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}