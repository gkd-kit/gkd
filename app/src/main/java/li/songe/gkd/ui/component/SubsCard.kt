package li.songe.gkd.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.gkd.R
import li.songe.gkd.subs.data.GkdApp
import li.songe.gkd.subs.data.GkdSubs

@Composable
fun SubsCard(gkdSubs: GkdSubs) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gkdSubs.desc ?: "简单描述", maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = gkdSubs.appList.size.toString())
        }
        Spacer(modifier = Modifier.width(5.dp))
//        Box() {
//            Text(text = "几秒前更新", fontSize = 5.sp)
        Image(
            painter = painterResource(R.drawable.ic_menu),
            contentDescription = "",
            modifier = Modifier
                .clickable {

                }
                .size(20.dp)
        )
//        }

    }
}

@Preview(backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
fun PreviewSubsCard() {
    Surface(modifier = Modifier.width(200.dp)) {
        SubsCard(
            GkdSubs(
                version = 1,
                appList = listOf(GkdApp(id = "com.tencent.mm", groupList = listOf()))
            )
        )
    }
}