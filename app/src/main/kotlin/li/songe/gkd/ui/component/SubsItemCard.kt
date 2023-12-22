package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.safeRemoteBaseUrls


@Composable
fun SubsItemCard(
    subsItem: SubsItem,
    rawSubscription: RawSubscription?,
    index: Int,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (rawSubscription != null) {
                Row {
                    Text(
                        text = index.toString() + ". " + (rawSubscription.name),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row {
                    Text(
                        text = formatTimeAgo(subsItem.mtime),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val sourceText =
                        if (subsItem.id < 0) {
                            "本地来源"
                        } else if (subsItem.updateUrl != null && safeRemoteBaseUrls.any { s ->
                                subsItem.updateUrl.startsWith(
                                    s
                                )
                            }) {
                            "可信来源"
                        } else {
                            "未知来源"
                        }
                    Text(text = sourceText, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row {
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
                    val apps = rawSubscription.apps
                    val groupsSize = rawSubscription.allGroupSize
                    val ruleNumText = if (groupsSize > 0) {
                        if (apps.isNotEmpty()) {
                            "${apps.size}应用/${groupsSize}规则组"
                        } else {
                            "${groupsSize}规则组"
                        }
                    } else {
                        "暂无规则"
                    }
                    Text(
                        text = ruleNumText,
                        fontSize = 14.sp
                    )
                }
            } else {
                Text(
                    text = "本地无订阅文件,请下拉刷新",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
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
