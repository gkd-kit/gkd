package li.gkd.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_count_summary
import li.gkd.app.resources.action_log_description
import li.gkd.app.resources.action_log_open
import li.gkd.app.resources.action_log_recent_prefix
import li.gkd.app.resources.action_log_title
import li.gkd.app.resources.activity_log_open
import li.gkd.app.resources.activity_log_title
import li.gkd.app.resources.activity_record_description
import li.gkd.app.resources.adb_permission_restricted
import li.gkd.app.resources.adb_restricted_privilege_notice
import li.gkd.app.resources.app_permission_restricted
import li.gkd.app.resources.app_rule_summary_open
import li.gkd.app.resources.data_load_failed
import li.gkd.app.resources.documentation_description
import li.gkd.app.resources.documentation_open
import li.gkd.app.resources.gkd_learn_more
import li.gkd.app.resources.item_toggle_description
import li.gkd.app.resources.permission_restricted_privilege_notice
import li.gkd.app.resources.persistent_notification
import li.gkd.app.resources.privilege_service_open
import li.gkd.app.resources.privilege_service_state_connected
import li.gkd.app.resources.privilege_service_state_disconnected
import li.gkd.app.resources.privilege_service_state_lost
import li.gkd.app.resources.restriction_details_open
import li.gkd.app.resources.rules_empty
import li.gkd.app.resources.service_state
import li.gkd.app.resources.service_state_toggle
import li.gkd.app.resources.status_statistics_description
import li.gkd.app.resources.subscription_app_rule_counts
import li.gkd.app.resources.subscription_global_count
import li.gkd.app.resources.work_mode_open
import li.gkd.app.resources.work_mode_title
import li.gkd.app.rule.RuleGroupSnapshot
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPermissionRestrictionDialog
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.component.textSize
import li.gkd.app.ui.icon.GkAnimatedRocketIcon
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.ActivityLogRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.WorkModeRoute
import li.gkd.app.ui.navigation.setStatusServiceEnabled
import li.gkd.app.ui.navigation.switchAutomator
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.settings.appVersion
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.db.RuleGroupType
import org.jetbrains.compose.resources.stringResource

@Composable
fun dashboardSummary(state: Loadable<RuleGroupSnapshot>, actionCount: Long): String =
    when (state) {
        Loadable.Loading -> ""
        is Loadable.Failure -> stringResource(Res.string.data_load_failed)
        is Loadable.Ready -> {
            val groups = state.value.groups
            val rules =
                listOfNotNull(
                        if (groups.globalGroups.isNotEmpty())
                            stringResource(
                                Res.string.subscription_global_count,
                                groups.globalGroups.size.toString(),
                            )
                        else null,
                        if (groups.appGroupSize > 0)
                            stringResource(
                                Res.string.subscription_app_rule_counts,
                                groups.appSize.toString(),
                                groups.appGroupSize.toString(),
                            )
                        else null,
                    )
                    .joinToString("/")
                    .ifEmpty { stringResource(Res.string.rules_empty) }
            if (actionCount > 0)
                stringResource(
                    Res.string.action_count_summary,
                    rules,
                    actionCount.toString(),
                )
            else rules
        }
    }

@Composable
fun dashboardPage(
    window: AppWindow,
    vm: HomeViewModel,
    onNavigate: (AppRoute) -> Unit,
): ScaffoldExt {
    val appName = window.appVersion().appName
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val scopeApps by SettingsRepository.a11yScopeAppList.collectAsStateWithLifecycle()
    val platform = window.dashboardPlatformState()
    var showRestrictionDetails by rememberSaveable { mutableStateOf(false) }
    if (showRestrictionDetails) {
        GkPermissionRestrictionDialog(
            privilegeAvailable = platform.privilegeAvailable,
            capabilities = platform.privilegeCapabilities,
            appRestrictions = platform.appRestrictions,
            onDismiss = { showRestrictionDetails = false },
            onPrivilege = { onNavigate(PrivilegeServiceRoute) },
        )
    }
    val latestState by vm.latestState.collectAsStateWithLifecycle()
    val rules by ruleGroupState.collectAsStateWithLifecycle()
    val actionCount by SettingsRepository.actionCount.collectAsStateWithLifecycle()
    val appCatalog by AppInfoRepository.state.collectAsStateWithLifecycle()
    val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
    val latest = latestState.value?.record
    val scroll = rememberColumnScrollState()
    ResetPageScrollOnRequest(vm.homeState, BottomNavItem.Dashboard, scroll::resetScrollAndAwait)
    val privilegeLabel =
        stringResource(
            when (platform.privilegeStatus) {
                DashboardPrivilegeStatus.Connected -> Res.string.privilege_service_state_connected
                DashboardPrivilegeStatus.DisconnectedDesired ->
                    Res.string.privilege_service_state_lost
                DashboardPrivilegeStatus.Disconnected ->
                    Res.string.privilege_service_state_disconnected
            }
        )
    val openPrivilege = stringResource(Res.string.privilege_service_open)
    val connected = platform.privilegeStatus == DashboardPrivilegeStatus.Connected
    val warningTitle =
        when {
            connected && platform.privilegeCapabilities?.restricted == true ->
                Res.string.adb_permission_restricted
            !connected && platform.restricted -> Res.string.app_permission_restricted
            else -> null
        }
    // Keep the outgoing text until the exit transition finishes.
    var retainedWarningTitle by remember { mutableStateOf(warningTitle) }
    if (warningTitle != null) retainedWarningTitle = warningTitle
    val openRestrictionDetails = stringResource(Res.string.restriction_details_open)
    return ScaffoldExt(
        BottomNavItem.Dashboard,
        modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scroll.scrollBehavior,
                title = { Text(appName) },
                actions = {
                    GkTooltipIconButtonBox(privilegeLabel) {
                        IconButton(
                            onClick = { onNavigate(PrivilegeServiceRoute) },
                            modifier =
                                Modifier.semantics {
                                    onClick(
                                        label = openPrivilege,
                                        action = null,
                                    )
                                },
                        ) {
                            GkAnimatedRocketIcon(
                                active =
                                    platform.privilegeStatus == DashboardPrivilegeStatus.Connected,
                                contentDescription = privilegeLabel,
                                tint =
                                    if (
                                        platform.privilegeStatus ==
                                            DashboardPrivilegeStatus.DisconnectedDesired
                                    )
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(scroll.scrollState)
                .padding(padding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2),
        ) {
            Column {
                AnimatedVisibility(
                    visible = warningTitle != null,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(bottom = itemHorizontalPadding / 2)
                                .semantics(mergeDescendants = true) {
                                    onClick(label = openRestrictionDetails, action = null)
                                },
                        shape = MaterialTheme.shapes.large,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                        onClick = { showRestrictionDetails = true },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(itemVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GkIcon(GkIcons.WarningAmber)
                                    Text(
                                        stringResource(requireNotNull(retainedWarningTitle)),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Text(
                                    stringResource(
                                        if (
                                            retainedWarningTitle ==
                                                Res.string.adb_permission_restricted
                                        )
                                            Res.string.adb_restricted_privilege_notice
                                        else Res.string.permission_restricted_privilege_notice
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            GkIcon(GkIcons.KeyboardArrowRight)
                        }
                    }
                }
                ServiceStatusCard(
                    stringResource(platform.subtitle(store, scopeApps)),
                    platform.serviceEnabled(store, scopeApps),
                    { enabled ->
                        when (val route = platform.authorizationRoute(enabled, store, scopeApps)) {
                            PrivilegeServiceRoute -> showRestrictionDetails = true
                            null -> window.switchAutomator()
                            else -> onNavigate(route)
                        }
                    },
                    AutomatorModeOption.objects.findOption(store.automatorMode).label,
                    { onNavigate(WorkModeRoute) },
                )
            }
            PageSwitchItemCard(
                GkIcons.Notifications,
                stringResource(Res.string.persistent_notification),
                stringResource(Res.string.status_statistics_description),
                platform.statusRunning && store.enableStatusService,
                window::setStatusServiceEnabled,
            )
            TriggerOverviewCard(
                dashboardSummary(rules, actionCount),
                HomeDataText.latest(
                    latest,
                    subscriptions.value?.subscriptions.orEmpty(),
                    appCatalog.snapshot?.apps.orEmpty(),
                ),
                (latest?.groupType == RuleGroupType.Global),
                { onNavigate(ActionLogRoute()) },
                { latest?.let { onNavigate(AppConfigRoute(it.appId, focusLog = it)) } },
                (latestState is Loadable.Failure),
            )
            if (platform.activityRunning) {
                PageItemCard(
                    GkIcons.Layers,
                    stringResource(Res.string.activity_log_title),
                    stringResource(Res.string.activity_record_description),
                    stringResource(Res.string.activity_log_open),
                    { onNavigate(ActivityLogRoute) },
                )
            }
            PageItemCard(
                GkIcons.HelpOutline,
                stringResource(Res.string.gkd_learn_more),
                stringResource(Res.string.documentation_description),
                stringResource(Res.string.documentation_open),
                { onNavigate(WebViewRoute(AppLinks.Home)) },
            )
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
        modifier =
            Modifier.fillMaxWidth().semantics {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(imageVector = imageVector) {
            Column(modifier = Modifier.weight(1f)) {
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
    val onClick = { onCheckedChange(!checked) }
    val toggleLabel = stringResource(Res.string.item_toggle_description, title)
    Card(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                this.onClick(label = toggleLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(imageVector = imageVector) {
            Column(modifier = Modifier.weight(1f)) {
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
    val onStatusClick = { onCheckedChange(!checked) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
    ) {
        IconTextCard(
            imageVector = GkIcons.Memory,
            modifier =
                Modifier.semantics(mergeDescendants = true) {}
                    .clickable(
                        onClickLabel = stringResource(Res.string.service_state_toggle),
                        onClick = onStatusClick,
                    ),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.service_state),
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
            modifier =
                Modifier.padding(
                    start = itemVerticalPadding + 40.dp + itemHorizontalPadding,
                    end = itemVerticalPadding,
                )
        )
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .clickable(
                        onClickLabel = stringResource(Res.string.work_mode_open),
                        onClick = onModeClick,
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
                modifier = Modifier.padding(horizontal = 10.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Text(
                text = stringResource(Res.string.work_mode_title),
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
        modifier = modifier.fillMaxWidth().padding(itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkIcon(
            imageVector = imageVector,
            modifier =
                Modifier.clip(CircleShape)
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
    latestLoadFailed: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .clickable(
                        onClickLabel = stringResource(Res.string.action_log_open),
                        onClick = onOpenActionLog,
                    )
                    .padding(
                        start = itemVerticalPadding,
                        end = itemVerticalPadding,
                        top = itemVerticalPadding,
                        bottom = itemVerticalPadding / 2,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GkIcon(
                imageVector = GkIcons.History,
                modifier =
                    Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp)
                        .size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.action_log_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(Res.string.action_log_description),
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = itemVerticalPadding)) {
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (latestLoadFailed) {
                Text(
                    text =
                        stringResource(Res.string.action_log_recent_prefix) +
                            stringResource(Res.string.data_load_failed),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (latestRecordDesc != null) {
                Row(
                    modifier =
                        Modifier.padding(horizontal = 4.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(
                                onClickLabel = stringResource(Res.string.app_rule_summary_open),
                                onClick = onOpenLatestRecord,
                            )
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GkGroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = stringResource(Res.string.action_log_recent_prefix),
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
