package li.songe.gkd.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.navigation.navigate
import kotlinx.coroutines.delay
import li.songe.gkd.MainActivity
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.AboutPageDestination
import li.songe.gkd.ui.destinations.RecordPageDestination
import li.songe.gkd.ui.destinations.SnapshotPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStore
import li.songe.gkd.util.usePollState
import li.songe.gkd.util.useTask

val controlNav = BottomNavItem(label = "主页", icon = SafeR.ic_home, route = "settings")

@Composable
fun ControlPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val vm = hiltViewModel<ControlVm>()
    val recordCount by vm.recordCountFlow.collectAsState()

    val store by storeFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(backgroundColor = Color(0xfff8f9f9), title = {
                Text(
                    text = "搞快点", color = Color.Black
                )
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(
                    state = rememberScrollState()
                )
                .padding(0.dp, 10.dp)
                .padding(padding)
        ) {
            val gkdAccessRunning by usePollState { GkdAbService.isRunning() }
            TextSwitch(name = "无障碍授权",
                desc = "用于获取屏幕信息,点击屏幕上的控件",
                gkdAccessRunning,
                onCheckedChange = scope.launchAsFn<Boolean> {
                    if (!it) return@launchAsFn
                    ToastUtils.showShort("请先启动无障碍服务")
                    delay(500)
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                })


            Spacer(modifier = Modifier.height(5.dp))
            TextSwitch(name = "服务开启",
                desc = "保持服务开启,根据订阅规则匹配屏幕目标节点",
                checked = store.enableService,
                onCheckedChange = {
                    updateStore(
                        store.copy(
                            enableService = it
                        )
                    )
                })

            Spacer(modifier = Modifier.height(5.dp))
            Text(text = "规则已触发 $recordCount 次", modifier = Modifier.padding(10.dp, 0.dp))
            Spacer(modifier = Modifier.height(5.dp))
            Text(text = "最近触发规则组: 微信朋友圈广告", modifier = Modifier.padding(10.dp, 0.dp))

            SettingItem(title = "快照记录", onClick = scope.useTask().launchAsFn {
                navController.navigate(SnapshotPageDestination)
            })

            SettingItem(title = "触发记录", onClick = scope.useTask().launchAsFn {
                navController.navigate(RecordPageDestination)
            })

            SettingItem(title = "关于", onClick = scope.useTask().launchAsFn {
                navController.navigate(AboutPageDestination)
            })

        }
    }
}
