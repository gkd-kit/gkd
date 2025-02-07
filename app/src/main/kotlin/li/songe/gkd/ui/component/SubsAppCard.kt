package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.ui.style.appItemPadding


@Composable
fun SubsAppCard(
    rawApp: RawSubscription.RawApp,
    appInfo: AppInfo? = null,
    subsConfig: SubsConfig? = null,
    enableSize: Int = rawApp.groups.count { g -> g.enable ?: true },
    onClick: (() -> Unit)? = null,
    onValueChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clickable {
                onClick?.invoke()
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appInfo = appInfo)
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(
                appId = rawApp.id,
                appInfo = appInfo,
                fallbackName = rawApp.name,
            )
            if (rawApp.groups.isNotEmpty()) {
                val enableDesc = when (enableSize) {
                    0 -> "${rawApp.groups.size}组规则/${rawApp.groups.size}关闭"
                    rawApp.groups.size -> "${rawApp.groups.size}组规则"
                    else -> "${rawApp.groups.size}组规则/${enableSize}启用/${rawApp.groups.size - enableSize}关闭"
                }
                Text(
                    text = enableDesc,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        key(rawApp.id) {
            Switch(
                checked = subsConfig?.enable ?: (appInfo != null),
                onCheckedChange = onValueChange,
            )
        }
    }
}


