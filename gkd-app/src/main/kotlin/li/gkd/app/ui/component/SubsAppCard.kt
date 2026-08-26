package li.gkd.app.ui.component

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.data.AppConfig
import li.gkd.app.data.AppInfo
import li.gkd.app.data.RawSubscription
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.app.ui.style.appItemPadding


@Composable
fun SubsAppCard(
    rawApp: RawSubscription.RawApp,
    appInfo: AppInfo?,
    appConfig: AppConfig?,
    enableSize: Int?,
    switchEnabled: Boolean,
    onClick: (() -> Unit),
    onValueChange: ((Boolean) -> Unit),
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .appItemPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(appId = rawApp.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(appInfo = appInfo, fallbackName = rawApp.name)
            if (rawApp.groups.isNotEmpty()) {
                val enableDesc = when (enableSize) {
                    null -> "${rawApp.groups.size}组规则"
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
        if (blockMatchAppListFlow.collectAsStateWithLifecycle().value.contains(rawApp.id)) {
            PerfIcon(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp),
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        PerfSwitch(
            key = rawApp.id,
            checked = if (switchEnabled) {
                appConfig?.enable ?: (appInfo != null)
            } else {
                true
            },
            onCheckedChange = onValueChange,
            enabled = switchEnabled,
        )
    }
}
