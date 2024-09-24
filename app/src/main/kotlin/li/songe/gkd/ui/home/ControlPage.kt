package li.songe.gkd.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.generated.destinations.ClickLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SlowGroupPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import li.songe.gkd.MainActivity
import li.songe.gkd.a11yServiceEnabledFlow
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.ManageService
import li.songe.gkd.service.switchA11yService
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.HOME_PAGE_URL
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openUri
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle

val controlNav = BottomNavItem(label = "主页", icon = Icons.Outlined.Home)

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<HomeVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    return ScaffoldExt(navItem = controlNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = controlNav.label,
                )
            }, actions = {
                IconButton(onClick = throttle {
                    navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                }) {
                    Icon(
                        imageVector = Icons.Outlined.RocketLaunch,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = throttle { context.openUri(HOME_PAGE_URL) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                }
            })
        }
    ) { padding ->
        val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
        val subsStatus by vm.subsStatusFlow.collectAsState()
        val store by storeFlow.collectAsState()
        val ruleSummary by ruleSummaryFlow.collectAsState()

        val a11yRunning by A11yService.isRunning.collectAsState()
        val manageRunning by ManageService.isRunning.collectAsState()
        val a11yServiceEnabled by a11yServiceEnabledFlow.collectAsState()

        // 无障碍故障: 设置中无障碍开启, 但是实际 service 没有运行
        val a11yBroken = !writeSecureSettings && !a11yRunning && a11yServiceEnabled

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(padding)
        ) {
            if (writeSecureSettings) {
                TextSwitch(
                    title = "服务状态",
                    subtitle = if (store.enableService) "无障碍服务正在运行" else "无障碍服务已关闭",
                    checked = store.enableService,
                    onCheckedChange = {
                        switchA11yService()
                    })
            }
            if (!writeSecureSettings && !a11yRunning) {
                AuthCard(
                    title = "无障碍授权",
                    desc = if (a11yBroken) "服务故障,请重新授权" else "授权使无障碍服务运行",
                    onAuthClick = {
                        navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                    })
            }

            TextSwitch(
                title = "常驻通知",
                subtitle = "显示运行状态及统计数据",
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        storeFlow.value = store.copy(
                            enableStatusService = true
                        )
                        ManageService.start()
                    } else {
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                        ManageService.stop()
                    }
                })

            SettingItem(
                title = "触发记录",
                subtitle = "如误触可定位关闭规则",
                onClick = {
                    navController.toDestinationsNavigator().navigate(ClickLogPageDestination)
                }
            )

            if (store.enableActivityLog) {
                SettingItem(
                    title = "界面记录",
                    subtitle = "记录打开的应用及界面",
                    onClick = {
                        navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                    }
                )
            }

            if (ruleSummary.slowGroupCount > 0) {
                SettingItem(
                    title = "耗时查询-${ruleSummary.slowGroupCount}",
                    subtitle = "可能导致触发缓慢或更多耗电",
                    onClick = {
                        navController.toDestinationsNavigator().navigate(SlowGroupPageDestination)
                    }
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (latestRecordDesc != null) {
                    Text(
                        text = "最近点击: $latestRecordDesc",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
