package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.AppInfo
import li.songe.gkd.ui.style.appItemPadding
import li.songe.gkd.util.throttle

@Composable
fun AppCheckBoxCard(
    appInfo: AppInfo,
    checked: Boolean,
    onCheckedChange: (() -> Unit),
) {
    Row(
        modifier = Modifier
            .clickable(onClick = throttle(onCheckedChange))
            .clearAndSetSemantics {
                contentDescription = "应用：${appInfo.name}"
                stateDescription = if (checked) "已加入名单" else "未加入名单"
                onClick(
                    label = if (checked) "从名单中移除" else "加入名单",
                    action = null
                )
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appId = appInfo.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(appInfo = appInfo)
            Text(
                text = appInfo.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        PerfCheckbox(
            key = appInfo.id,
            checked = checked,
        )
    }
}