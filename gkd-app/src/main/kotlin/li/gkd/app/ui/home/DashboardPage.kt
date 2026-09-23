package li.gkd.app.ui.home

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

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
import androidx.compose.material3.IconButton
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.icon.GkAnimatedRocketIcon
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
import li.gkd.app.data.subscription.SubscriptionState
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
import li.gkd.app.store.AppStore.actualA11yScopeAppList
import li.gkd.app.store.AppStore.actionCountFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.feature.log.ActionLogRoute
import li.gkd.app.feature.log.ActivityLogRoute
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.WebViewRoute
import li.gkd.app.feature.settings.WorkModeRoute
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.HOME_PAGE_URL
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.statusText
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.db.RuleGroupType
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.component.textSize

@Composable
fun useDashboardPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<DashboardVm>()
    val ruleSummary by SubscriptionState.ruleSummaryFlow.collectAsStateWithLifecycle()
    val actionCount by actionCountFlow.collectAsStateWithLifecycle()
    val subsStatus = ruleSummary.statusText(actionCount)
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
            GkTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = stringResource(R.string.app_name)
                )
            }, actions = {
                val (contentDescription, contentColor) = when (privilegeServiceStatus) {
                    PrivilegeServiceStatus.Connected -> UiStrings.privilege_service_state_connected to MaterialTheme.colorScheme.onSurfaceVariant
                    PrivilegeServiceStatus.Disconnected -> UiStrings.privilege_service_state_disconnected to MaterialTheme.colorScheme.onSurfaceVariant
                    PrivilegeServiceStatus.DisconnectedDesired -> UiStrings.privilege_service_state_lost to MaterialTheme.colorScheme.error
                }
                GkTooltipIconButtonBox(contentDescription) {
                    IconButton(
                        modifier = Modifier.semantics { onClick(label = UiStrings.privilege_service_open, action = null) },
                        onClick = { mainVm.navigatePage(PrivilegeServiceRoute) },
                    ) {
                        GkAnimatedRocketIcon(
                            active = privilegeServiceStatus == PrivilegeServiceStatus.Connected,
                            contentDescription = contentDescription,
                            tint = contentColor,
                        )
                    }
                }
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
                            this.onClick(label = UiStrings.privilege_service_open, action = null)
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
                        GkIcon(imageVector = GkIcons.WarningAmber)
                        Text(
                            modifier = Modifier.weight(1f),
                            text = UiStrings.permission_restricted_privilege_notice,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        GkIcon(imageVector = GkIcons.KeyboardArrowRight)
                    }
                }
            }
            if (store.useA11y || actualA11yScopeAppList.contains(topAppIdFlow.collectAsStateWithLifecycle().value)) {
                ServiceStatusCard(
                    subtitle = if (a11yRunning) {
                        UiStrings.a11y_running
                    } else if (mainVm.a11yServiceEnabledFlow.collectAsStateWithLifecycle().value) {
                        UiStrings.a11y_fault
                    } else if (writeSecureSettings) {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            UiStrings.a11y_partially_disabled
                        } else {
                            UiStrings.a11y_stopped
                        }
                    } else {
                        UiStrings.a11y_unauthorized
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
                        UiStrings.automation_running
                    } else if (privilegeContext == null) {
                        UiStrings.automation_unauthorized
                    } else {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            UiStrings.automation_partially_disabled
                        } else {
                            UiStrings.automation_stopped
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
                imageVector = GkIcons.Notifications,
                title = UiStrings.persistent_notification,
                subtitle = UiStrings.status_statistics_description,
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = {
                    if (it) {
                        vm.scope.launchUi {
                            mainVm.enableStatusService()
                        }
                    } else {
                        vm.stopStatusService()
                    }
                },
            )

            val latestRecord by SubscriptionState.latestRecordFlow.collectAsStateWithLifecycle()
            val latestRecordDesc by SubscriptionState.latestRecordDescFlow.collectAsStateWithLifecycle()
            TriggerOverviewCard(
                subsStatus = subsStatus,
                latestRecordDesc = latestRecordDesc,
                latestRecordIsGlobal = latestRecord?.groupType == RuleGroupType.Global,
                onOpenActionLog = { mainVm.navigatePage(ActionLogRoute()) },
                onOpenLatestRecord = {
                    latestRecord?.let {
                        mainVm.navigatePage(AppConfigRoute(appId = it.appId, focusLog = it))
                    }
                },
            )

            if (ActivityService.isRunning.collectAsStateWithLifecycle().value) {
                PageItemCard(
                    title = UiStrings.activity_log_title,
                    subtitle = UiStrings.activity_record_description,
                    imageVector = GkIcons.Layers,
                    onClickLabel = UiStrings.activity_log_open,
                    onClick = {
                        mainVm.navigatePage(ActivityLogRoute)
                    })
            }

            PageItemCard(
                title = UiStrings.gkd_learn_more,
                subtitle = UiStrings.documentation_description,
                imageVector = GkIcons.HelpOutline,
                onClickLabel = UiStrings.documentation_open,
                onClick = {
                    mainVm.navigatePage(WebViewRoute(initUrl = HOME_PAGE_URL))
                })
            GkPageBottomSpace()
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
                this.onClick(label = UiStrings.item_toggle_description(title), action = null)
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
            GkSwitch(
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
            imageVector = GkIcons.Memory,
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .clickable(
                    onClickLabel = UiStrings.service_state_toggle,
                    onClick = onStatusClick,
                ),
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = UiStrings.service_state,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            GkSwitch(
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
                    onClickLabel = UiStrings.work_mode_open,
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
            GkIcon(
                imageVector = GkIcons.AutoMode,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Text(
                text = UiStrings.work_mode_title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = mode,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GkIcon(
                imageVector = GkIcons.KeyboardArrowRight,
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
        GkIcon(
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
                    onClickLabel = UiStrings.action_log_open,
                    onClick = throttle(onOpenActionLog),
                )
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = itemVerticalPadding,
                    bottom = itemVerticalPadding / 2
                ), verticalAlignment = Alignment.CenterVertically
        ) {
            GkIcon(
                imageVector = GkIcons.Equalizer,
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
                    text = UiStrings.action_log_title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = UiStrings.action_log_description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GkIcon(
                imageVector = GkIcons.KeyboardArrowRight,
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
                            onClickLabel = UiStrings.app_rule_summary_open,
                            onClick = throttle(onOpenLatestRecord),
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        GkGroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = UiStrings.action_log_recent_prefix,
                            isGlobal = latestRecordIsGlobal,
                            text = latestRecordDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    GkIcon(
                        imageVector = GkIcons.KeyboardArrowRight,
                        modifier = Modifier.textSize(style = MaterialTheme.typography.bodyMedium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(itemVerticalPadding))
        }
    }
}
