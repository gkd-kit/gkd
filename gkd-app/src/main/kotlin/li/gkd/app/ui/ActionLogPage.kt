package li.gkd.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import li.gkd.app.data.SubsConfig
import li.gkd.app.ui.component.AppNameText
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.FixedTimeText
import li.gkd.app.ui.component.GroupNameText
import li.gkd.app.ui.component.AppDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.useSubs
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.throttle
import li.gkd.app.util.launchTry
import li.gkd.app.util.toast

@Serializable
data class ActionLogRoute(
    val subsId: Long? = null,
    val appId: String? = null,
) : NavKey

@Composable
fun ActionLogPage(route: ActionLogRoute) {
    val subsId = route.subsId
    val appId = route.appId
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { ActionLogVm(route) }
    val dialogState by vm.dialogStateFlow.collectAsStateWithLifecycle()
    val scope = vm.scope
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(list.itemCount > 0)
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = {
                        mainVm.popPage()
                    },
                )
            },
            title = {
                val title = "触发记录"
                val titleModifier = Modifier.noRippleClickable {
                    pageScrollState.resetScroll()
                }
                if (subsId != null) {
                    TowLineText(
                        title = title,
                        subtitle = useSubs(subsId)?.name ?: subsId.toString(),
                        modifier = titleModifier,
                    )
                } else if (appId != null) {
                    TowLineText(
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
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle {
                            scope.launchTry {
                                val text = if (subsId != null) {
                                    "确定删除当前订阅所有触发记录?"
                                } else if (appId != null) {
                                    "确定删除当前应用所有触发记录?"
                                } else {
                                    "确定删除所有触发记录?"
                                }
                                if (!mainVm.dialogRequests.confirm(
                                    title = "删除记录",
                                    text = text,
                                    error = true,
                                )) return@launchTry
                                vm.deleteLogs()
                                toast("删除成功")
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
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (list.itemCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                    EmptyText(text = "暂无数据")
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
                if (actionLog.groupType == SubsConfig.AppGroupType) {
                    mainVm.navigatePage(
                        SubsAppGroupListRoute(
                            actionLog.subsId, actionLog.appId, actionLog.groupKey
                        )
                    )
                } else if (actionLog.groupType == SubsConfig.GlobalGroupType) {
                    mainVm.navigatePage(
                        SubsGlobalGroupListRoute(
                            actionLog.subsId, actionLog.groupKey
                        )
                    )
                }
            },
            onToggleGlobalAppExclusion = {
                scope.launchTry {
                    vm.toggleGlobalAppExclusion()
                    toast("更新成功")
                }
            },
            onToggleActivityExclusion = {
                scope.launchTry {
                    vm.toggleActivityExclusion()
                    toast("更新成功")
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
                    AppNameText(appId = actionLog.appId, modifier = Modifier.weight(1f))
                    PerfIcon(
                        imageVector = PerfIcon.KeyboardArrowRight,
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
                FixedTimeText(
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
                            text = "null",
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                    if (subsId == null) {
                        Row {
                            Text(text = subscription?.name ?: "id=${actionLog.subsId}")
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
                                    text = "v${actionLog.subsVersion}",
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
                        GroupNameText(
                            isGlobal = actionLog.groupType == SubsConfig.GlobalGroupType,
                            text = groupDesc,
                            color = textColor,
                        )
                        val ruleDesc = rule?.name ?: (if ((group?.rules?.size ?: 0) > 1) {
                            val keyDesc = actionLog.ruleKey?.let { "key=$it, " } ?: ""
                            "${keyDesc}index=${actionLog.ruleIndex}"
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
    onToggleGlobalAppExclusion: () -> Unit,
    onToggleActivityExclusion: () -> Unit,
) {
    val actionLog = state.actionLog

    AppDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            ItemText(
                text = "查看规则",
                onClick = onOpenRule,
            )
            HorizontalDivider()

            if (actionLog.groupType == SubsConfig.GlobalGroupType) {
                val appChecked = state.globalAppChecked
                if (appChecked != null) {
                    ItemText(
                        text = if (appChecked) "在此应用禁用" else "移除在此应用的禁用",
                        onClick = onToggleGlobalAppExclusion,
                    )
                    HorizontalDivider()
                }
            }

            if (actionLog.activityId != null) {
                ItemText(
                    text = if (state.activityDisabled) "移除在此页面的禁用" else "在此页面禁用",
                    onClick = onToggleActivityExclusion,
                )
                HorizontalDivider()
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
