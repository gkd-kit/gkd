package li.songe.gkd.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.ui.DebugPage
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.util.LocaleString.Companion.localeString
import li.songe.gkd.util.Storage
import li.songe.router.LocalRouter

@Composable
fun SettingsPage() {
    Column(
        modifier = Modifier
            .verticalScroll(
                state = rememberScrollState()
            )
            .padding(20.dp, 0.dp)
    ) {

        val context = LocalContext.current as MainActivity
//        val composableScope = rememberCoroutineScope()
        val router = LocalRouter.current

        var enableService by remember { mutableStateOf(Storage.settings.enableService) }
        TextSwitch(
            text = "保持服务${(if (enableService) "开启" else "关闭")}",
            checked = enableService,
            onCheckedChange = {
                enableService = it
                Storage.settings.commit {
                    this.enableService = it
                }
            }
        )
        var excludeFromRecents by remember { mutableStateOf(Storage.settings.excludeFromRecents) }
        TextSwitch(
            text = "在[最近任务]界面中隐藏本应用",
            checked = excludeFromRecents,
            onCheckedChange = {
                excludeFromRecents = it
                Storage.settings.commit {
                    this.excludeFromRecents = it
                }
            }
        )

        var enableConsoleLogOut by remember { mutableStateOf(Storage.settings.enableConsoleLogOut) }
        TextSwitch(
            text = "保持日志输出到控制台",
            checked = enableConsoleLogOut,
            onCheckedChange = {
                enableConsoleLogOut = it
                LogUtils.getConfig().setConsoleSwitch(it)
                Storage.settings.commit {
                    this.enableConsoleLogOut = it
                }
            }
        )

        var notificationVisible by remember { mutableStateOf(Storage.settings.notificationVisible) }
        TextSwitch(text = "通知栏显示", checked = notificationVisible,
            onCheckedChange = {
                notificationVisible = it
                Storage.settings.commit {
                    this.notificationVisible = it
                }
            })

        Button(onClick = {
            router.navigate(DebugPage)
        }) {
            Text(text = "调试模式")
        }

        Text(text = "多语言自动切换:" + localeString(R.string.app_name))
    }
}