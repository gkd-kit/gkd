package li.gkd.app.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import li.gkd.app.model.showActivityId
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_log_activity_unknown
import li.gkd.app.resources.action_log_open_app
import li.gkd.app.resources.action_log_title
import li.gkd.app.resources.data_load_failed
import li.gkd.app.resources.loading_progress
import li.gkd.app.resources.page_exclusion_add_current
import li.gkd.app.resources.page_exclusion_remove
import li.gkd.app.resources.rule_actions
import li.gkd.app.resources.rule_enable
import li.gkd.app.resources.rule_enable_in_app
import li.gkd.app.resources.rule_group_key_description
import li.gkd.app.resources.rule_index_description
import li.gkd.app.resources.rule_key_prefix
import li.gkd.app.resources.rule_missing
import li.gkd.app.resources.rule_view
import li.gkd.app.resources.subscription_id_description
import li.gkd.app.resources.update_success
import li.gkd.app.resources.version_prefixed
import li.gkd.app.rule.RuleSetting
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkLogTimeText
import li.gkd.app.ui.component.GkLogTimeline
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.gkLogTimelineRail
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.text.getSync
import li.gkd.db.ActionLog
import li.gkd.db.RuleGroupType
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActionLogPage(
    route: ActionLogRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    appIcon: @Composable (String, Dp) -> Unit,
) {
    val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
    val subsId = route.subsId
    val appId = route.appId
    val appScoped = subsId == null && appId != null
    val vm = viewModel { ActionLogViewModel(route) }
    val dialogState by vm.dialogStateFlow.collectAsStateWithLifecycle()
    val scope = vm.scope
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(list.itemCount > 0)
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = {
                            onBack()
                        },
                    )
                },
                title = {
                    val title = stringResource(Res.string.action_log_title)
                    val titleModifier = Modifier.noRippleClickable {
                        pageScrollState.resetScroll()
                    }
                    if (subsId != null) {
                        GkTwoLineText(
                            title = subscriptions.value?.subscriptions?.get(subsId)?.name
                                ?: subsId.toString(),
                            subtitle = title,
                            modifier = titleModifier,
                        )
                    } else if (appId != null) {
                        GkTwoLineText(
                            title = title,
                            subtitle = appId,
                            showApp = true,
                            appContent = { id, fallback -> GkAppNameText(id, fallback) },
                            modifier = titleModifier,
                        )
                    } else {
                        Text(
                            text = title,
                            modifier = titleModifier,
                        )
                    }
                })
        },
        content = { contentPadding ->
            GkLogTimeline(
                appLabel = { rememberRuleControlEnvironment().apps[it]?.name ?: it },
                appIcon = { appIcon(it, 24.dp) },
                appName = { id, modifier ->
                    GkAppNameText(
                        id,
                        modifier = modifier,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                onOpenApp = { onNavigate(AppConfigRoute(it)) },
                items = list,
                listState = listState,
                key = { it.actionLog.id },
                appId = { it.actionLog.appId },
                time = { it.actionLog.ctime },
                modifier = Modifier.scaffoldPadding(contentPadding),
                showAppHeaders = !appScoped,
                contextChanged = { previous, current ->
                    !sameActionLogContext(previous.actionLog, current.actionLog)
                },
                contextHeader = { item, previous ->
                    ActionLogContextHeader(
                        item = item,
                        previousLog = previous?.actionLog,
                        includeSubscriptionName = subsId == null,
                    )
                },
            ) { entry ->
                ActionLogEntry(
                    modifier = Modifier.animateListItem(),
                    item = entry,
                    onClick = { vm.showActionLog(entry.actionLog) },
                )
            }
        })

    dialogState?.let { state ->
        ActionLogDialog(
            state = state,
            onDismissRequest = vm::dismissActionLog,
            showAppContext = appId == null,
            onOpenApp = {
                vm.dismissActionLog()
                onNavigate(AppConfigRoute(state.actionLog.appId))
            },
            onOpenRule = {
                vm.dismissActionLog()
                val actionLog = state.actionLog
                if (actionLog.groupType == RuleGroupType.App) {
                    onNavigate(
                        SubsAppGroupListRoute(
                            actionLog.subsId, actionLog.appId, actionLog.groupKey
                        )
                    )
                } else if (actionLog.groupType == RuleGroupType.Global) {
                    onNavigate(
                        SubsGlobalGroupListRoute(
                            actionLog.subsId, actionLog.groupKey
                        )
                    )
                }
            },
            onSettingChange = { setting ->
                val request = vm.prepareSwitch(state)
                launchUi(scope, showToast) {
                    vm.applySwitch(request, setting).failureMessage()
                        ?.let { showToast(it) }
                }
            },
            onToggleActivityExclusion = {
                launchUi(scope, showToast) {
                    vm.updateActivityExclusion(state)
                    showToast(Res.string.update_success.getSync())
                }
            },
        )
    }
}

private fun sameActionLogContext(first: ActionLog, second: ActionLog): Boolean =
    first.activityId == second.activityId &&
            first.subsId == second.subsId &&
            first.subsVersion == second.subsVersion

@Composable
private fun ActionLogContextHeader(
    item: ActionLogListItem,
    previousLog: ActionLog?,
    includeSubscriptionName: Boolean,
) {
    val activityChanged = previousLog == null || previousLog.activityId != item.actionLog.activityId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .padding(
                start = 20.dp, end = itemHorizontalPadding,
                top = if (previousLog == null) 4.dp else 8.dp, bottom = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (activityChanged) {
                GkIcon(
                    imageVector = GkIcons.Layers,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        ActionLogContextText(
            item = item,
            showActivity = activityChanged,
            showSubscriptionLine = previousLog == null ||
                    previousLog.subsId != item.actionLog.subsId ||
                    previousLog.subsVersion != item.actionLog.subsVersion,
            includeSubscriptionName = includeSubscriptionName,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionLogContextText(
    item: ActionLogListItem,
    showActivity: Boolean,
    showSubscriptionLine: Boolean,
    includeSubscriptionName: Boolean,
    modifier: Modifier = Modifier,
) {
    val actionLog = item.actionLog
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showActivity) {
            Text(
                text = actionLog.showActivityId
                    ?: stringResource(Res.string.action_log_activity_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (showSubscriptionLine) {
            Text(
                text = if (includeSubscriptionName) {
                    listOf(
                        item.subscription?.name ?: stringResource(
                            Res.string.subscription_id_description,
                            actionLog.subsId
                        ),
                        stringResource(Res.string.version_prefixed, actionLog.subsVersion),
                    ).joinToString(" · ")
                } else {
                    stringResource(Res.string.version_prefixed, actionLog.subsVersion)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionLogEntry(
    modifier: Modifier = Modifier,
    item: ActionLogListItem,
    onClick: () -> Unit,
) {
    val (actionLog, group, rule) = item
    val ruleName = rule?.name?.takeIf { it.isNotBlank() } ?: if ((group?.rules?.size ?: 0) > 1) {
        val key = actionLog.ruleKey?.let { stringResource(Res.string.rule_key_prefix, it) } ?: ""
        stringResource(Res.string.rule_index_description, key, actionLog.ruleIndex)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = itemHorizontalPadding, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            GkGroupNameText(
                isGlobal = actionLog.groupType == RuleGroupType.Global,
                text = group?.name
                    ?: if (item.subscriptionResolved) stringResource(Res.string.rule_missing)
                    else stringResource(
                        Res.string.rule_group_key_description,
                        actionLog.groupKey.toString()
                    ),
                color = if (group == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ruleName != null) {
                Text(
                    text = ruleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GkLogTimeText(actionLog.ctime)
    }
}

@Composable
private fun ActionLogDialog(
    state: ActionLogDialogState,
    onDismissRequest: () -> Unit,
    showAppContext: Boolean,
    onOpenApp: () -> Unit,
    onOpenRule: () -> Unit,
    onSettingChange: (RuleSetting) -> Unit,
    onToggleActivityExclusion: () -> Unit,
) {
    val actionLog = state.actionLog
    val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()

    val environment = rememberRuleControlEnvironment()
    GkRuleSettingsSheet(
        title = state.group?.name ?: stringResource(Res.string.rule_actions),
        subtitle = if (showAppContext) environment.apps[actionLog.appId]?.name
            ?: actionLog.appId else null,
        onDismissRequest = onDismissRequest,
    ) {
        val configs = state.configs.value
        if (state.configs is Loadable.Failure || subscriptions is Loadable.Failure) {
            Text(stringResource(Res.string.data_load_failed), Modifier.padding(16.dp))
        } else if (state.configs is Loadable.Loading || subscriptions is Loadable.Loading) {
            Text(stringResource(Res.string.loading_progress), Modifier.padding(16.dp))
        }
        if (state.subscription != null && state.group != null && configs != null) {
            val control =
                environment.resolve(state.subscription, state.group, actionLog.appId, configs)
            GkRuleSettingsContent(
                control, onSettingChange,
                title = if (state.group is RawSubscription.RawGlobalGroup) stringResource(Res.string.rule_enable_in_app) else stringResource(
                    Res.string.rule_enable
                )
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (showAppContext) {
                ItemText(text = stringResource(Res.string.action_log_open_app), onClick = onOpenApp)
            }
            if (state.subscription != null && state.group != null) {
                ItemText(text = stringResource(Res.string.rule_view), onClick = onOpenRule)
            }
            if (actionLog.activityId != null && state.subscription != null && state.group != null && configs != null) {
                ItemText(
                    text = if (state.activityDisabled) stringResource(Res.string.page_exclusion_remove) else stringResource(
                        Res.string.page_exclusion_add_current
                    ),
                    onClick = onToggleActivityExclusion,
                )
            }
        }
    }
}

@Composable
private fun ItemText(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .clickable(onClick = onClick)
        .fillMaxWidth()
        .padding(16.dp)
    Text(
        modifier = modifier,
        text = text,
        color = color,
    )
}
