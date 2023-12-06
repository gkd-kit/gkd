package li.songe.gkd.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun ControlPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = hiltViewModel<ControlVm>()
    val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
    val subsStatus by vm.subsStatusFlow.collectAsState()
    val store by storeFlow.collectAsState()

    val gkdAccessRunning by usePollState { GkdAbService.isRunning() }
    val notifEnabled by usePollState {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    var notifDialog by remember { mutableStateOf(false) }
    val notifRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted) {
            notifDialog = true
        }
    }
    val canDrawOverlays by usePollState { Settings.canDrawOverlays(context) }


    Column(
        modifier = Modifier.verticalScroll(
            state = rememberScrollState()
        )
    ) {
        if (!notifEnabled) {
            AuthCard(title = "通知权限", desc = "用于启动后台服务,展示服务运行状态", onAuthClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            })
            Divider()
        }

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
                    text = "点击记录", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "如误触可在此快速定位关闭规则", fontSize = 14.sp
                )
            }
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
        Divider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp, 5.dp)
        ) {
            Text(text = subsStatus, fontSize = 18.sp)
            if (latestRecordDesc != null) {
                Text(
                    text = "最近点击: $latestRecordDesc", fontSize = 14.sp
                )
            }
        }

    }

    if (notifDialog) {
        AlertDialog(
            title = {
                Text("通知权限被拒绝")
            },
            text = {
                Text("需要通知权限才能展示服务运行状态，是否打开系统设置？")
            },
            onDismissRequest = {
                notifDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.applicationInfo.uid)
                        context.startActivity(intent)
                        notifDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        notifDialog = false
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
}
