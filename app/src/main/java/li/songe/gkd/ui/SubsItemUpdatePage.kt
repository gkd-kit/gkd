package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.component.TextSwitch

object SubsItemUpdatePage : Page<SubsItem, SubsItem?> {
    override val path = "SubsItemInsertPage"
    override val defaultParams: SubsItem
        get() = throw Error("not defaultParams")
    override val content: @Composable BoxScope.(
        params: SubsItem,
        router: Router<SubsItem?>
    ) -> Unit = { params, router ->

        val scope = rememberCoroutineScope()

        var comment by remember { mutableStateOf(params.comment) }
        var url by remember { mutableStateOf(params.updateUrl) }
        var enable by remember { mutableStateOf(params.enable) }

        Column(modifier = Modifier.padding(10.dp, 0.dp)) {
            StatusBar()
            Text(text = "订阅配置")
            TextSwitch(
                text = "启用",
                checked = enable,
                onCheckedChange = { enable = it }
            )

            TextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(text = "备注") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "清除文本",
                        modifier = Modifier
                            .clickable {
                                comment = ""
                            }
                    )
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(text = "链接") },
                isError = !URLUtil.isNetworkUrl(url),
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                scope.launch {
                    val newSubsItem = params.copy(
                        enable = enable,
                        comment = comment,
                        updateUrl = url,
                        mtime = System.currentTimeMillis()
                    )
                    RoomX.update(newSubsItem)
                    router.back(newSubsItem)
                }
            }) {
                Text(text = "修改")
            }
        }
    }
}