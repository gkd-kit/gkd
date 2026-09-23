package li.gkd.app.feature.log

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.data.date
import li.gkd.app.data.showActivityId
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupListRoute
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.db.RuleGroupType
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.data.RawSubscription
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.useSubs

@Serializable
data class ActionLogRoute(
    val subsId: Long? = null,
    val appId: String? = null,
) : NavKey

@Composable
fun ActionLogPage(route: ActionLogRoute) {
    val subsId = route.subsId
    val appId = route.appId
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
            },
            actions = {
                if (list.itemCount > 0) {
                    GkIconButton(
                        imageVector = GkIcons.Delete,
                        onClick = throttle {
                            val text = if (subsId != null) {
                                UiStrings.action_log_delete_subscription_confirmation
                            } else if (appId != null) {
                                UiStrings.action_log_delete_app_confirmation
                            } else {
                                UiStrings.action_log_delete_all_confirmation
                            }
                            scope.launchUi {
                                if (!mainVm.dialogRequests.confirm(
                                    title = UiStrings.action_delete_records,
                                    text = text,
                                    error = true,
                                )) return@launchUi
                                vm.deleteLogs()
                                toast(UiStrings.delete_success)
                            }
                        },
                    )
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            items(
                count = list.itemCount,
                key = list.itemKey { item -> item.actionLog.id }
            ) { i ->
                val item = list[i]
                if (item != null) {
                    val lastItem = if (i > 0) list[i - 1] else null
                    ActionLogCard(
                        modifier = Modifier.animateListItem(),
                        i = i,
                        item = item,
                        lastItem = lastItem,
                        onClick = {
                            vm.showActionLog(item.actionLog)
                        },
                        subsId = subsId,
                        appId = appId,
                        onOpenApp = {
                            mainVm.navigatePage(AppConfigRoute(it))
                        },
                    )
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                if (list.itemCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                    GkEmptyState(text = UiStrings.data_empty)
                } else {
                    GkPageBottomSpace()
                }
            }
        }
    })

    dialogState?.let { state ->
        ActionLogDialog(
            state = state,
            onDismissRequest = vm::dismissActionLog,
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


@Composable
private fun ActionLogCard(
    modifier: Modifier = Modifier,
    i: Int,
    item: ActionLogListItem,
    lastItem: ActionLogListItem?,
    onClick: () -> Unit,
    onOpenApp: (String) -> Unit,
    subsId: Long?,
    appId: String?,
) {
    val (actionLog, group, rule, subscription) = item
    val lastActionLog = lastItem?.actionLog
    val isDiffApp = actionLog.appId != lastActionLog?.appId
    val verticalPadding = if (i == 0) 0.dp else if (isDiffApp) 12.dp else 8.dp
    val indicatorOffset = if (appId == null) 2.dp else 0.dp
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = itemHorizontalPadding / 2,
                end = itemHorizontalPadding / 2,
                top = verticalPadding
            )
    ) {
        if (isDiffApp && appId == null) {
            Row(
                modifier = Modifier
                    .padding(start = itemHorizontalPadding / 4)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = throttle { onOpenApp(actionLog.appId) })
                    .fillMaxWidth()
                    .padding(start = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    Spacer(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                            .size(4.dp)
                    )
                    GkAppNameText(appId = actionLog.appId, modifier = Modifier.weight(1f))
                    GkIcon(
                        imageVector = GkIcons.KeyboardArrowRight,
                        modifier = Modifier
                            .iconTextSize()
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(start = itemHorizontalPadding / 4)
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .padding(start = itemHorizontalPadding / 4)
                .drawBehind {
                    drawRect(
                        color = indicatorColor,
                        topLeft = Offset(indicatorOffset.toPx(), 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
                .padding(start = indicatorOffset + 10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                GkFixedTimeText(
                    text = actionLog.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    val showActivityId = actionLog.showActivityId
                    if (showActivityId != null) {
                        Text(
                            text = showActivityId,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    } else {
                        Text(
                            text = UiStrings.value_null,
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                    if (subsId == null) {
                        Row {
                            Text(text = subscription?.name ?: UiStrings.subscription_id_description(actionLog.subsId))
                            val lineHeightDp = LocalDensity.current.run {
                                LocalTextStyle.current.lineHeight.toDp()
                            }
                            Row(
                                modifier = Modifier
                                    .height(lineHeightDp)
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = UiStrings.version_prefixed(actionLog.subsVersion),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val groupDesc = group?.name.toString()
                        val textColor = LocalContentColor.current.let {
                            if (group?.name == null) it.copy(alpha = 0.5f) else it
                        }
                        GkGroupNameText(
                            isGlobal = actionLog.groupType == RuleGroupType.Global,
                            text = groupDesc,
                            color = textColor,
                        )
                        val ruleDesc = rule?.name ?: (if ((group?.rules?.size ?: 0) > 1) {
                            val keyDesc = actionLog.ruleKey?.let { UiStrings.rule_key_prefix(it) } ?: ""
                            UiStrings.rule_index_description(keyDesc, actionLog.ruleIndex)
                        } else {
                            null
                        })
                        if (ruleDesc != null) {
                            Text(
                                text = ruleDesc,
                                modifier = Modifier.padding(start = 8.dp),
                                color = LocalContentColor.current.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLogDialog(
    state: ActionLogDialogState,
    onDismissRequest: () -> Unit,
    onOpenRule: () -> Unit,
    onSettingChange: (RuleSetting) -> Unit,
    onToggleActivityExclusion: () -> Unit,
) {
    val actionLog = state.actionLog

    val environment = rememberRuleControlEnvironment()
    GkRuleSettingsSheet(
        title = state.group?.name ?: UiStrings.rule_actions,
        subtitle = environment.apps[actionLog.appId]?.name ?: actionLog.appId,
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
fun ItemText(
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
