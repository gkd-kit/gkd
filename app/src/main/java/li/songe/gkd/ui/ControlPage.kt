package li.songe.gkd.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.ClickLogPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage
import li.songe.gkd.util.usePollState

val controlNav = BottomNavItem(label = "主页", icon = SafeR.ic_home, route = "settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = hiltViewModel<ControlVm>()
    val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
    val subsStatus by vm.subsStatusFlow.collectAsState()
    val store by storeFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = "GKD"
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

            val notifEnabled by usePollState {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
            if (!notifEnabled) {
                AuthCard(title = "通知权限",
                    desc = "用于启动后台服务,展示服务运行状态",
                    onAuthClick = {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.applicationInfo.uid)
                        context.startActivity(intent)
                    })
                Divider()
            }

            val gkdAccessRunning by usePollState { GkdAbService.isRunning() }
            if (!gkdAccessRunning) {
                AuthCard(title = "无障碍权限",
                    desc = "用于获取屏幕信息,点击屏幕上的控件",
                    onAuthClick = {
                        if (notifEnabled) {
                            appScope.launchTry(Dispatchers.IO) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                // android.content.ActivityNotFoundException
                                // https://bugly.qq.com/v2/crash-reporting/crashes/d0ce46b353/113010?pid=1
                                context.startActivity(intent)
                            }
                        } else {
                            ToastUtils.showShort("必须先开启[通知权限]")
                        }
                    })
                Divider()
            }


            val canDrawOverlays by usePollState { Settings.canDrawOverlays(context) }
            if (!canDrawOverlays) {
                AuthCard(title = "悬浮窗权限",
                    desc = "用于后台提示,显示保存快照按钮等功能",
                    onAuthClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    })
                Divider()
            }

            if (gkdAccessRunning) {
                TextSwitch(name = "服务开启",
                    desc = "保持服务开启,根据订阅规则匹配屏幕目标节点",
                    checked = store.enableService,
                    onCheckedChange = {
                        updateStorage(
                            storeFlow, store.copy(
                                enableService = it
                            )
                        )
                    })
                Divider()

                TextSwitch(name = "无障碍前台",
                    desc = "某些机型需添加前台悬浮窗才能正常工作",
                    checked = store.enableAbFloatWindow,
                    onCheckedChange = {
                        updateStorage(
                            storeFlow, store.copy(
                                enableAbFloatWindow = it
                            )
                        )
                    })
                Divider()
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        navController.navigate(ClickLogPageDestination)
                    }
                    .padding(10.dp, 5.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subsStatus, fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (latestRecordDesc != null) "最近点击: $latestRecordDesc" else "暂无记录",
                        fontSize = 14.sp
                    )
                }
                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "")
            }

        }
    }
}
