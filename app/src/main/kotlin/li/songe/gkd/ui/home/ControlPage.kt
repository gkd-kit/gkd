package li.songe.gkd.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.ActionLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.AppConfigPageDestination
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import li.songe.gkd.MainActivity
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.ActivityService
import li.songe.gkd.service.StatusService
import li.songe.gkd.service.a11yPartDisabledFlow
import li.songe.gkd.service.switchA11yService
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.GroupNameText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfSwitch
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.textSize
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.HOME_PAGE_URL
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.latestRecordDescFlow
import li.songe.gkd.util.latestRecordFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    return ScaffoldExt(
        navItem = BottomNavItem.Control,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(text = stringResource(SafeR.app_name))
            }, actions = {
                PerfIconButton(
                    imageVector = PerfIcon.RocketLaunch,
                    onClickLabel = "前往无障碍授权页面",
                    contentDescription = "无障碍授权",
                    onClick = throttle {
                        mainVm.navigatePage(AuthA11YPageDestination)
                    },
                )
            })
        }
    ) { contentPadding ->
        val store by storeFlow.collectAsState()

        val a11yRunning by A11yService.isRunning.collectAsState()
        val manageRunning by StatusService.isRunning.collectAsState()
        val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
        ) {
            PageSwitchItemCard(
                imageVector = PerfIcon.Memory,
                title = "服务状态",
                subtitle = if (a11yRunning) {
                    "无障碍正在运行"
                } else if (mainVm.a11yServiceEnabledFlow.collectAsState().value) {
                    "无障碍发生故障"
                } else if (writeSecureSettings) {
                    if (store.enableService && a11yPartDisabledFlow.collectAsState().value) {
                        "无障碍局部关闭"
                    } else {
                        "无障碍已关闭"
                    }
                } else {
                    "无障碍未授权"
                },
                checked = a11yRunning,
                onCheckedChange = vm.viewModelScope.launchAsFn { newEnabled ->
                    if (newEnabled && !writeSecureSettingsState.value) {
                        mainVm.navigatePage(AuthA11YPageDestination)
                    } else {
                        switchA11yService()
                    }
                },
            )

            PageSwitchItemCard(
                imageVector = PerfIcon.Notifications,
                title = "常驻通知",
                subtitle = "显示运行状态及统计数据",
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        StatusService.requestStart(context)
                    } else {
                        StatusService.stop()
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                    }
                },
            )

            ServerStatusCard()

            PageItemCard(
                title = "触发记录",
                subtitle = "规则误触可定位关闭",
                imageVector = PerfIcon.History,
                onClickLabel = "打开触发记录页面",
                onClick = {
                    mainVm.navigatePage(ActionLogPageDestination())
                }
            )

            if (ActivityService.isRunning.collectAsState().value) {
                PageItemCard(
                    title = "界面日志",
                    subtitle = "记录打开的应用及界面",
                    imageVector = PerfIcon.Layers,
                    onClickLabel = "打开界面日志页面",
                    onClick = {
                        mainVm.navigatePage(ActivityLogPageDestination)
                    }
                )
            }

            PageItemCard(
                title = "了解 GKD",
                subtitle = "查阅规则文档和常见问题",
                imageVector = PerfIcon.HelpOutline,
                onClickLabel = "打开 GKD 文档页面",
                onClick = {
                    mainVm.navigatePage(WebViewPageDestination(initUrl = HOME_PAGE_URL))
                }
            )
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}


@Composable
private fun PageItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = throttle(fn = onClick)
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageSwitchItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val onClick = throttle { onCheckedChange(!checked) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.onClick(label = "切换$title", action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            PerfSwitch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PerfIcon(
            imageVector = imageVector,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(itemHorizontalPadding))
        content()
    }
}

@Composable
private fun ServerStatusCard() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = "不执行操作", action = null)
            },
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors,
        onClick = {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = itemVerticalPadding,
                    bottom = itemVerticalPadding / 2
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Equalizer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "数据概览",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val usedSubsItemCount by vm.usedSubsItemCountFlow.collectAsState()
                AnimatedVisibility(usedSubsItemCount > 0) {
                    Text(
                        text = "已开启 $usedSubsItemCount 条订阅",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = itemVerticalPadding)
        ) {
            val subsStatus by vm.subsStatusFlow.collectAsState()
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val latestRecordDesc by latestRecordDescFlow.collectAsState()
            if (latestRecordDesc != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = "前往应用的规则汇总页面", onClick = throttle {
                            latestRecordFlow.value?.let {
                                mainVm.navigatePage(
                                    AppConfigPageDestination(
                                        appId = it.appId,
                                        focusLog = it
                                    )
                                )
                            }
                        })
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        GroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = "最近触发: ",
                            isGlobal = latestRecordFlow.collectAsState().value?.groupType == SubsConfig.GlobalGroupType,
                            text = latestRecordDesc ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PerfIcon(
                        imageVector = PerfIcon.KeyboardArrowRight,
                        modifier = Modifier.textSize(style = MaterialTheme.typography.bodyMedium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(itemVerticalPadding))
        }
    }
}
