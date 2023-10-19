package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.safeRemoteBaseUrls


@Composable
fun SubsItemCard(
    subsItem: SubsItem,
    subscriptionRaw: SubscriptionRaw?,
    index: Int,
    onMenuClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Box {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (subsItem.id < 0) {
                Text(text = "本地来源", fontSize = 12.sp)
            } else if (subsItem.updateUrl != null && safeRemoteBaseUrls.any { s ->
                    subsItem.updateUrl.startsWith(
                        s
                    )
                }) {
                Text(text = "可信来源", fontSize = 12.sp)
            } else {
                Text(text = "未知来源", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (subscriptionRaw != null) {
                    Row {
                        Text(
                            text = index.toString() + ". " + (subscriptionRaw.name),
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
                        if (subsItem.id >= 0) {
                            Text(
                                text = "v" + (subscriptionRaw.version.toString()),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        val apps = subscriptionRaw.apps
                        val groupsSize = apps.sumOf { it.groups.size }
                        if (groupsSize > 0) {
                            Text(
                                text = "${apps.size}应用/${groupsSize}规则组", fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = "无规则", fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "本地无订阅文件,请刷新",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(5.dp))

            if (subsItem.id != -2L) { // 本地订阅不要显示菜单按钮
                IconButton(onClick = { onMenuClick?.invoke() }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "more",
                        modifier = Modifier.size(30.dp)
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
}
