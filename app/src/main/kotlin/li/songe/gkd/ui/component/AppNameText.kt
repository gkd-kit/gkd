package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.otherUserMapFlow
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
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (info?.isSystem == true) {
            val style = LocalTextStyle.current
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                modifier = Modifier
                    .clickable(onClick = throttle(fn = { toast("${info.name} 是系统应用") }))
                    .size(style.fontSize.value.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = info?.name ?: fallbackName ?: appId ?: error("appId is required"),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        if (info?.userId != null) {
            Spacer(modifier = Modifier.width(4.dp))
            val userInfo = otherUserMapFlow.collectAsState().value[info.userId]
            Text(
                text = "(${userInfo?.name ?: info.userId})",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}