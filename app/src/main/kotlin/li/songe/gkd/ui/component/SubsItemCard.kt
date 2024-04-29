package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.map
import li.songe.gkd.util.subsLoadErrorsFlow
import li.songe.gkd.util.subsRefreshErrorsFlow


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

    Row(
        verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (rawSubscription != null) {
                Text(
                    text = index.toString() + ". " + (rawSubscription.name),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row {
                    Text(text = subsItem.sourceText, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formatTimeAgo(subsItem.mtime),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    if (subsItem.id >= 0) {
                        Text(
                            text = "v" + (rawSubscription.version.toString()),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = rawSubscription.numText, fontSize = 14.sp
                )
            } else {
                Text(
                    text = "${index}. id:${subsItem.id}",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                val color = if (subsLoadError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Unspecified
                }
                Text(
                    text = subsLoadError?.message ?: "加载订阅中...",
                    fontSize = 14.sp,
                    color = color
                )
            }
            if (subsRefreshError != null) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "刷新错误: ${subsRefreshError?.message}",
                    fontSize = 14.sp,
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
