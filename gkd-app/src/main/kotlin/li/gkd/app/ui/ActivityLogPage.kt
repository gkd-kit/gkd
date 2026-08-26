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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.serialization.Serializable
import li.gkd.app.data.ActivityLog
import li.gkd.app.ui.component.AppNameText
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.FixedTimeText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data object ActivityLogRoute : NavKey

@Composable
fun ActivityLogPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<ActivityLogVm>()
    val scope = vm.scope

    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val logCount = list.itemCount
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(list.itemCount > 0)
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                Text(
                    text = "界面日志",
                    modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll),
                )
            },
            actions = {
                if (logCount > 0) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle {
                            scope.launchTry {
                                if (!mainVm.dialogRequests.confirm(
                                    title = "删除日志",
                                    text = "确定删除所有界面日志?",
                                    error = true,
                                )) return@launchTry
                                vm.deleteAll()
                                toast("删除成功")
                            }
                        }
                    )
                }
            }
        )
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            items(
                count = list.itemCount,
                key = list.itemKey { it.id }
            ) { i ->
                val actionLog = list[i]
                if (actionLog != null) {
                    val lastActionLog = if (i > 0) list[i - 1] else null
                    ActivityLogCard(
                        i = i,
                        activityLog = actionLog,
                        lastActivityLog = lastActionLog,
                        onOpenApp = {
                            mainVm.navigatePage(AppConfigRoute(actionLog.appId))
                        },
                        onShowDetails = {
                            mainVm.textDialog.showText(
                                listOfNotNull(
                                    appInfoMapFlow.value[actionLog.appId]?.name,
                                    actionLog.appId,
                                    actionLog.showActivityId,
                                ).joinToString("\n"),
                            )
                        },
                    )
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (logCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                    EmptyText(text = "暂无数据")
                }
            }
        }
    }
}

@Composable
private fun ActivityLogCard(
    i: Int,
    activityLog: ActivityLog,
    lastActivityLog: ActivityLog?,
    onOpenApp: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val isDiffApp = activityLog.appId != lastActivityLog?.appId
    val verticalPadding = if (i == 0) 0.dp else if (isDiffApp) 12.dp else 8.dp
    val showActivityId = activityLog.showActivityId
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = itemHorizontalPadding / 2,
                end = itemHorizontalPadding / 2,
                top = verticalPadding
            )
    ) {
        if (isDiffApp) {
            Row(
                modifier = Modifier
                    .padding(start = itemHorizontalPadding / 4)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = throttle(onOpenApp))
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
                    AppNameText(appId = activityLog.appId, modifier = Modifier.weight(1f))
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
                .clickable(onClick = onShowDetails)
                .fillMaxWidth()
                .padding(start = itemHorizontalPadding / 4)
                .drawBehind {
                    drawRect(
                        color = indicatorColor,
                        topLeft = Offset(2.dp.toPx(), 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
                .padding(start = 12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                FixedTimeText(
                    text = activityLog.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
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
                }
            }
        }
    }
}
