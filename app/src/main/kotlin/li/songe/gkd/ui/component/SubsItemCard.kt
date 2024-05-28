package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.map
import li.songe.gkd.util.subsLoadErrorsFlow
import li.songe.gkd.util.subsRefreshErrorsFlow
import li.songe.gkd.util.subsRefreshingFlow


@Composable
fun SubsItemCard(
    subsItem: SubsItem,
    rawSubscription: RawSubscription?,
    index: Int,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val subsLoadError by remember(subsItem.id) {
        subsLoadErrorsFlow.map(scope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshError by remember(subsItem.id) {
        subsRefreshErrorsFlow.map(scope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshing by subsRefreshingFlow.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (rawSubscription != null) {
                Text(
                    text = index.toString() + ". " + (rawSubscription.name),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = subsItem.sourceText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatTimeAgo(subsItem.mtime),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (subsItem.id >= 0) {
                        Text(
                            text = "v" + (rawSubscription.version.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    text = rawSubscription.numText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rawSubscription.groupsSize == 0) {
                        LocalContentColor.current.copy(alpha = 0.5f)
                    } else {
                        LocalContentColor.current
                    }
                )
            } else {
                Text(
                    text = "${index}. id:${subsItem.id}",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val color = if (subsLoadError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Unspecified
                }
                Text(
                    text = subsLoadError?.message
                        ?: if (subsRefreshing) "加载中..." else "文件不存在",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
            if (subsRefreshError != null) {
                Text(
                    text = "加载错误: ${subsRefreshError?.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = subsItem.enable,
            onCheckedChange = onCheckedChange,
        )
    }
}
