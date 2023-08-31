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
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.MainActivity
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.ClickLogPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage
import li.songe.gkd.util.usePollState

val controlNav = BottomNavItem(label = "主页", icon = SafeR.ic_home, route = "settings")

@Composable
fun ControlPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = hiltViewModel<ControlVm>()
    val latestRecordGroup by vm.latestRecordGroup.collectAsState()
    val subsStatus by vm.subsStatusFlow.collectAsState()
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
            if (!gkdAccessRunning) {
                AuthCard(title = "无障碍授权",
                    desc = "用于获取屏幕信息,点击屏幕上的控件",
                    onAuthClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    })
            }


            Spacer(modifier = Modifier.height(5.dp))
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

            Spacer(modifier = Modifier.height(5.dp))
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
                    Text(text = latestRecordGroup?.name?.let { "最近点击: $it" } ?: "暂无记录",
                        fontSize = 14.sp)
                }
                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null)
            }

        }
    }
}
