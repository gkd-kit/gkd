package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.ui.SubsAppInfoItem
import li.songe.gkd.ui.style.appItemPadding


@Composable
fun SubsAppCard(
    data: SubsAppInfoItem,
    enableSize: Int = data.rawApp.groups.count { g -> g.enable ?: true },
    onClick: (() -> Unit),
    onValueChange: ((Boolean) -> Unit),
) {
    val rawApp = data.rawApp
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .appItemPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(appId = data.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(appInfo = data.appInfo, fallbackName = data.rawApp.name)
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
        if (blockMatchAppListFlow.collectAsState().value.contains(data.id)) {
            PerfIcon(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp),
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        PerfSwitch(
            key = data.id,
            checked = data.appConfig?.enable ?: (data.appInfo != null),
            onCheckedChange = onValueChange,
        )
    }
}


