package li.songe.gkd.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.SubsItem
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.formatTimeAgo

@Composable
fun SubsItemCard(
    subsItem: SubsItem,
    onMenuClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(8.dp)
            .alpha(if (subsItem.enable) 1f else .3f),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = subsItem.order.toString() + ". " + subsItem.name,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                Text(
                    text = formatTimeAgo(subsItem.mtime) + "更新",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "v" + subsItem.version.toString(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(5.dp))
        Image(painter = painterResource(SafeR.ic_menu),
            contentDescription = "refresh",
            modifier = Modifier
                .clickable {
                    onMenuClick?.invoke()
                }
                .size(30.dp)

        )
//        Spacer(modifier = Modifier.width(5.dp))
//        Image(painter = painterResource(SafeR.ic_refresh),
//            contentDescription = "refresh",
//            modifier = Modifier
//                .clickable {
//                    onRefreshClick?.invoke()
//                }
//                .padding(4.dp)
//                .size(20.dp))
//        Spacer(modifier = Modifier.width(5.dp))
//        Image(painter = painterResource(SafeR.ic_share),
//            contentDescription = "share",
//            modifier = Modifier
//                .clickable {
//                    onShareClick?.invoke()
//                }
//                .padding(4.dp)
//                .size(20.dp))
//        Spacer(modifier = Modifier.width(5.dp))
//        Image(painter = painterResource(SafeR.ic_del),
//            contentDescription = "edit",
//            modifier = Modifier
//                .clickable {
//                    onDelClick?.invoke()
//                }
//                .padding(4.dp)
//                .size(20.dp))

        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = subsItem.enable,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview
@Composable
fun PreviewSubscriptionItemCard() {
    Surface(modifier = Modifier.width(400.dp)) {
        SubsItemCard(
            SubsItem(
                updateUrl = "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files",
                name = "GKD官方订阅",
                author = "gkd",
            )
        )
    }
}