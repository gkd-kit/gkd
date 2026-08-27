package li.gkd.app.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.db.SubsConfig
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.PrivilegeServiceStatus
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.privilegeServiceStatusFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.service.A11yService
import li.gkd.app.service.ActivityService
import li.gkd.app.service.StatusService
import li.gkd.app.service.a11yPartDisabledFlow
import li.gkd.app.service.switchAutomatorService
import li.gkd.app.service.topAppIdFlow
import li.gkd.app.store.actualA11yScopeAppList
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.ActionLogRoute
import li.gkd.app.ui.ActivityLogRoute
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.WebViewRoute
import li.gkd.app.ui.WorkModeRoute
import li.gkd.app.ui.component.GroupNameText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfSwitch
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.textSize
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.HOME_PAGE_URL
import li.gkd.app.util.latestRecordDescFlow
import li.gkd.app.util.latestRecordFlow
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle

@Composable
fun useDashboardPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<DashboardVm>()
    val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val privilegeContext by privilegeContextFlow.collectAsStateWithLifecycle()
    val privilegeServiceStatus by privilegeServiceStatusFlow.collectAsStateWithLifecycle()
    val automatorMode by mainVm.automatorModeFlow.collectAsStateWithLifecycle()
    val pageScrollState = rememberColumnScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val scrollState = pageScrollState.scrollState
    ResetPageScrollOnRequest(BottomNavItem.Dashboard, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.Dashboard,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = stringResource(R.string.app_name)
                )
            }, actions = {
                val (contentDescription, contentColor) = when (privilegeServiceStatus) {
                    PrivilegeServiceStatus.Connected -> "特权服务，已连接" to MaterialTheme.colorScheme.primary
                    PrivilegeServiceStatus.Disconnected -> "特权服务，未连接" to MaterialTheme.colorScheme.onSurfaceVariant
                    PrivilegeServiceStatus.DisconnectedDesired -> "特权服务，连接已中断" to MaterialTheme.colorScheme.error
                }
                PerfIconButton(
                    imageVector = PerfIcon.RocketLaunch,
                    onClickLabel = "前往特权服务页面",
                    contentDescription = contentDescription,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = contentColor,
                    ),
                    onClick = throttle {
                        mainVm.navigatePage(PrivilegeServiceRoute)
                    },
                )
            })
        }) { contentPadding ->
        val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
        val manageRunning by StatusService.isRunning.collectAsStateWithLifecycle()
        val writeSecureSettings by PermissionStates.writeSecureSettings.stateFlow.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
        ) {
            if (PermissionStates.appOpsRestrictedFlow.collectAsStateWithLifecycle().value) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            this.onClick(label = "前往特权服务页面", action = null)
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    onClick = throttle {
                        mainVm.navigatePage(PrivilegeServiceRoute)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(itemVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PerfIcon(imageVector = PerfIcon.WarningAmber)
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "检测到权限受限，请前往特权服务",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PerfIcon(imageVector = PerfIcon.KeyboardArrowRight)
                    }
                }
            }
            if (store.useA11y || actualA11yScopeAppList.contains(topAppIdFlow.collectAsStateWithLifecycle().value)) {
                ServiceStatusCard(
                    subtitle = if (a11yRunning) {
                        "无障碍正在运行"
                    } else if (mainVm.a11yServiceEnabledFlow.collectAsStateWithLifecycle().value) {
                        "无障碍发生故障"
                    } else if (writeSecureSettings) {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            "无障碍局部关闭"
                        } else {
                            "无障碍已关闭"
                        }
                    } else {
                        "无障碍未授权"
                    },
                    checked = a11yRunning,
                    onCheckedChange = { newEnabled ->
                        if (newEnabled && !PermissionStates.writeSecureSettings.value) {
                            mainVm.navigatePage(WorkModeRoute)
                        } else {
                            switchAutomatorService()
                        }
                    },
                    mode = automatorMode.label,
                    onModeClick = {
                        mainVm.navigatePage(WorkModeRoute)
                    },
                )
            } else {
                val automation by uiAutomationFlow.collectAsStateWithLifecycle()
                ServiceStatusCard(
                    subtitle = if (automation != null) {
                        "自动化正在运行"
                    } else if (privilegeContext == null) {
                        "自动化未授权"
                    } else {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            "自动化局部关闭"
                        } else {
                            "自动化已关闭"
                        }
                    },
                    checked = automation != null,
                    onCheckedChange = { newEnabled ->
                        if (newEnabled && privilegeContext == null) {
                            mainVm.navigatePage(PrivilegeServiceRoute)
                        } else {
                            switchAutomatorService()
                        }
                    },
                    mode = automatorMode.label,
                    onModeClick = {
                        mainVm.navigatePage(WorkModeRoute)
                    },
                )
            }

            PageSwitchItemCard(
                imageVector = PerfIcon.Notifications,
                title = "常驻通知",
                subtitle = "显示运行状态及统计数据",
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = {
                    if (it) {
                        vm.scope.launchTry {
                            StatusService.requestStart(mainVm)
                        }
                    } else {
                        vm.stopStatusService()
                    }
                },
            )

            val latestRecord by latestRecordFlow.collectAsStateWithLifecycle()
            val latestRecordDesc by latestRecordDescFlow.collectAsStateWithLifecycle()
            TriggerOverviewCard(
                subsStatus = subsStatus,
                latestRecordDesc = latestRecordDesc,
                latestRecordIsGlobal = latestRecord?.groupType == SubsConfig.GlobalGroupType,
                onOpenActionLog = { mainVm.navigatePage(ActionLogRoute()) },
                onOpenLatestRecord = {
                    latestRecord?.let {
                        mainVm.navigatePage(AppConfigRoute(appId = it.appId, focusLog = it))
                    }
                },
            )

            if (ActivityService.isRunning.collectAsStateWithLifecycle().value) {
                PageItemCard(
                    title = "界面日志",
                    subtitle = "记录打开的应用及界面",
                    imageVector = PerfIcon.Layers,
                    onClickLabel = "打开界面日志页面",
                    onClick = {
                        mainVm.navigatePage(ActivityLogRoute)
                    })
            }

            PageItemCard(
                title = "了解 GKD",
                subtitle = "查阅规则文档和常见问题",
                imageVector = PerfIcon.HelpOutline,
                onClickLabel = "打开 GKD 文档页面",
                onClick = {
                    mainVm.navigatePage(WebViewRoute(initUrl = HOME_PAGE_URL))
                })
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
private fun ServiceStatusCard(
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    mode: String,
    onModeClick: () -> Unit,
) {
    val onStatusClick = throttle { onCheckedChange(!checked) }
    val onModeRowClick = throttle(onModeClick)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
    ) {
        IconTextCard(
            imageVector = PerfIcon.Memory,
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .clickable(
                    onClickLabel = "切换服务状态",
                    onClick = onStatusClick,
                ),
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "服务状态",
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
        HorizontalDivider(
            modifier = Modifier.padding(
                start = itemVerticalPadding + 40.dp + itemHorizontalPadding,
                end = itemVerticalPadding,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .clickable(
                    onClickLabel = "前往工作模式页面",
                    onClick = onModeRowClick,
                )
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PerfIcon(
                imageVector = PerfIcon.AutoMode,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Text(
                text = "工作模式",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = mode,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
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
private fun TriggerOverviewCard(
    subsStatus: String,
    latestRecordDesc: String?,
    latestRecordIsGlobal: Boolean,
    onOpenActionLog: () -> Unit,
    onOpenLatestRecord: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .clickable(
                    onClickLabel = "打开触发记录页面",
                    onClick = throttle(onOpenActionLog),
                )
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = itemVerticalPadding,
                    bottom = itemVerticalPadding / 2
                ), verticalAlignment = Alignment.CenterVertically
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
                    text = "触发记录",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "规则误触可定位关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = itemVerticalPadding)
        ) {
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (latestRecordDesc != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(
                            onClickLabel = "前往应用的规则汇总页面",
                            onClick = throttle(onOpenLatestRecord),
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        GroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = "最近触发: ",
                            isGlobal = latestRecordIsGlobal,
                            text = latestRecordDesc,
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
