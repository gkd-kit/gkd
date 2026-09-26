package li.gkd.app.feature.log

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.serialization.Serializable
import li.gkd.app.MainViewModel
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.showActivityId
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupListRoute
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.component.GkLogTimeline
import li.gkd.app.ui.component.GkLogTimeText
import li.gkd.app.ui.component.gkLogTimelineRail
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.useSubs
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.ActionLog
import li.gkd.db.RuleGroupType

@Serializable
data class ActionLogRoute(
    val subsId: Long? = null,
    val appId: String? = null,
) : NavKey

@Composable
fun ActionLogPage(route: ActionLogRoute) {
    val subsId = route.subsId
    val appId = route.appId
    val appScoped = subsId == null && appId != null
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { ActionLogVm(route) }
    val dialogState by vm.dialogStateFlow.collectAsStateWithLifecycle()
    val scope = vm.scope
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(list.itemCount > 0)
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        GkTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                GkIconButton(
                    imageVector = GkIcons.ArrowBack,
                    onClick = {
                        mainVm.popPage()
                    },
                )
            },
            title = {
                val title = UiStrings.action_log_title
                val titleModifier = Modifier.noRippleClickable {
                    pageScrollState.resetScroll()
                }
                if (subsId != null) {
                    GkTwoLineText(
                        title = useSubs(subsId)?.name ?: subsId.toString(),
                        subtitle = title,
                        modifier = titleModifier,
                    )
                } else if (appId != null) {
                    GkTwoLineText(
                        title = title,
                        subtitle = appId,
                        showApp = true,
                        modifier = titleModifier,
                    )
                } else {
                    Text(
                        text = title,
                        modifier = titleModifier,
                    )
                }
            })
    }, content = { contentPadding ->
        GkLogTimeline(
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
                mainVm.navigatePage(AppConfigRoute(state.actionLog.appId))
            },
            onOpenRule = {
                vm.dismissActionLog()
                val actionLog = state.actionLog
                if (actionLog.groupType == RuleGroupType.App) {
                    mainVm.navigatePage(
                        SubsAppGroupListRoute(
                            actionLog.subsId, actionLog.appId, actionLog.groupKey
                        )
                    )
                } else if (actionLog.groupType == RuleGroupType.Global) {
                    mainVm.navigatePage(
                        SubsGlobalGroupListRoute(
                            actionLog.subsId, actionLog.groupKey
                        )
                    )
                }
            },
            onSettingChange = { setting ->
                val request = vm.prepareSwitch(state)
                scope.launchUi { vm.applySwitch(request, setting).failureMessage?.let { toast(it) } }
            },
            onToggleActivityExclusion = {
                scope.launchUi {
                    vm.updateActivityExclusion(state)
                    toast(UiStrings.update_success)
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
            .padding(start = 20.dp, end = itemHorizontalPadding,
                top = if (previousLog == null) 4.dp else 8.dp, bottom = 4.dp),
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
                text = actionLog.showActivityId ?: UiStrings.action_log_activity_unknown,
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
                        item.subscription?.name ?: UiStrings.subscription_id_description(actionLog.subsId),
                        UiStrings.version_prefixed(actionLog.subsVersion),
                    ).joinToString(" · ")
                } else {
                    UiStrings.version_prefixed(actionLog.subsVersion)
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
        val key = actionLog.ruleKey?.let { UiStrings.rule_key_prefix(it) } ?: ""
        UiStrings.rule_index_description(key, actionLog.ruleIndex)
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
                text = group?.name ?: UiStrings.rule_missing,
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

    val environment = rememberRuleControlEnvironment()
    GkRuleSettingsSheet(
        title = state.group?.name ?: UiStrings.rule_actions,
        subtitle = if (showAppContext) environment.apps[actionLog.appId]?.name ?: actionLog.appId else null,
        onDismissRequest = onDismissRequest,
    ) {
        if (state.subscription != null && state.group != null) {
            val control = environment.resolve(state.subscription, state.group, actionLog.appId, state.configs)
            GkRuleSettingsContent(control, onSettingChange,
                title = if (state.group is RawSubscription.RawGlobalGroup) UiStrings.rule_enable_in_app else UiStrings.rule_enable)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (showAppContext) {
                ItemText(text = UiStrings.action_log_open_app, onClick = onOpenApp)
            }
            ItemText(text = UiStrings.rule_view, onClick = onOpenRule)
            if (actionLog.activityId != null) {
                ItemText(
                    text = if (state.activityDisabled) UiStrings.page_exclusion_remove else UiStrings.page_exclusion_add_current,
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
        .clickable(onClick = throttle(onClick))
        .fillMaxWidth()
        .padding(16.dp)
    Text(
        modifier = modifier,
        text = text,
        color = color,
    )
}
