package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blankj.utilcode.util.PathUtils
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Subscription
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.Status
import java.io.File

object SubsItemInsertPage : Page<Unit, SubsItem?> {
    override val path = "SubsItemInsertPage"
    override val defaultParams = Unit
    override val content: @Composable BoxScope.(
        params: Unit,
        router: Router<SubsItem?>
    ) -> Unit = { _, router ->

        val scope = rememberCoroutineScope()

        var comment by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var enable by remember { mutableStateOf(true) }
        var subReqStatus: Status<Unit> by remember { mutableStateOf(Status.Empty) }

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
//            key(url) {
            // 对 TextField 使用 key 包裹会导致每输入一次字符键盘都会隐藏, 应该是 key 变化后会重置内部状态
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(text = "链接") },
                isError = !URLUtil.isNetworkUrl(url),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "清除文本",
                        modifier = Modifier
                            .clickable {
                                url = ""
                            }
                    )
                }
            )
//            }

            Button(onClick = {
                scope.launch {
                    subReqStatus = Status.Progress()
                    if (RoomX.select { SubsItem::updateUrl eq url }.isNotEmpty()) {
                        subReqStatus = Status.Error("链接已经存在,不可重复添加")
                        return@launch
                    }
                    var sub = try {
                        Singleton.client.get(url).body<Subscription>()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        subReqStatus = Status.Error(e)
                        return@launch
                    }
                    if (sub.updateUrl == null) {
                        sub = sub.copy(updateUrl = url)
                    }
                    File(
                        PathUtils.getExternalAppFilesPath()
                            .plus("/subscription/")
                    ).apply {
                        if ((!exists())) {
                            mkdir()
                        }
                    }
                    val fp = File(
                        PathUtils.getExternalAppFilesPath()
                            .plus("/subscription/")
                            .plus(System.currentTimeMillis())
                            .plus(".json")
                    )
                    withContext(Dispatchers.IO) {
                        fp.writeText(Subscription.stringify(sub))
                    }
                    val newSubsItem = SubsItem(
                        enable = enable,
                        comment = comment,
                        updateUrl = url,
                        filePath = fp.absolutePath,
                    )
                    RoomX.insert(newSubsItem)
                    subReqStatus = Status.Success(Unit)
                    router.back(newSubsItem)
                }
            }) {
                Text(text = "添加")
            }
        }

        when (val s = subReqStatus) {
            is Status.Error -> {
                Dialog(onDismissRequest = { subReqStatus = Status.Empty }) {
                    Box(
                        Modifier
                            .width(250.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        Text(text = s.value.toString())
                    }
                }
            }
            is Status.Progress -> {
                Dialog(onDismissRequest = {}) {
                    Box(
                        Modifier
                            .width(250.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        LinearProgressIndicator()
                    }
                }
            }
            is Status.Success -> {}
            Status.Empty -> {}
        }
    }
}