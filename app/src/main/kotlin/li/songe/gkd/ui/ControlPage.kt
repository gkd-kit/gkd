package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.ManageService
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.ClickLogPageDestination
import li.songe.gkd.util.HOME_PAGE_URL
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.checkOrRequestNotifPermission
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage
import li.songe.gkd.util.usePollState

val controlNav = BottomNavItem(label = "主页", icon = Icons.Default.Home)

@Composable
fun ControlPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = hiltViewModel<ControlVm>()
    val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
    val subsStatus by vm.subsStatusFlow.collectAsState()
    val store by storeFlow.collectAsState()

    val gkdAccessRunning by GkdAbService.isRunning.collectAsState()
    val manageRunning by ManageService.isRunning.collectAsState()
    val canDrawOverlays by usePollState { Settings.canDrawOverlays(context) }
    val canNotif by usePollState {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        modifier = Modifier.verticalScroll(
            state = rememberScrollState()
        )
    ) {
        if (!gkdAccessRunning) {
            AuthCard(
                title = "无障碍权限",
                desc = "用于获取屏幕信息,点击屏幕上的控件",
                onAuthClick = {
                    appScope.launchTry {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        // android.content.ActivityNotFoundException
                        context.startActivity(intent)
                    }
                })
        } else {
            TextSwitch(
                name = "服务开启",
                desc = "保持服务开启,根据订阅规则匹配屏幕目标节点",
                checked = store.enableService,
                onCheckedChange = {
                    updateStorage(
                        storeFlow, store.copy(
                            enableService = it
                        )
                    )
                })
        }
        Divider()

        if (!canNotif) {
            AuthCard(title = "通知权限",
                desc = "用于显示各类服务状态数据及前后台提示",
                onAuthClick = {
                    checkOrRequestNotifPermission(context)
                })
            Divider()
        }

        if (!canDrawOverlays) {
            AuthCard(
                title = "悬浮窗权限",
                desc = "用于后台提示,显示保存快照按钮等功能",
                onAuthClick = {
                    appScope.launchTry {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                })
            Divider()
        }

        TextSwitch(
            name = "常驻通知",
            desc = "在通知栏显示服务运行状态及统计数据",
            checked = manageRunning && store.enableStatusService,
            onCheckedChange = {
                if (it) {
                    if (!checkOrRequestNotifPermission(context)) {
                        return@TextSwitch
                    }
                    updateStorage(
                        storeFlow, store.copy(
                            enableStatusService = true
                        )
                    )
                    ManageService.start(context)
                } else {
                    updateStorage(
                        storeFlow, store.copy(
                            enableStatusService = false
                        )
                    )
                    ManageService.stop(context)
                }
            })
        Divider()

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    appScope.launchTry {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW, Uri.parse(HOME_PAGE_URL)
                            )
                        )
                    }
                }
                .padding(10.dp, 5.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用说明", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = HOME_PAGE_URL,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null)
        }
        Divider()

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
}
