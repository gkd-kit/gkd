package li.songe.gkd.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.MainActivity
import li.songe.gkd.data.ActivityLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.FixedTimeText
import li.songe.gkd.ui.component.LocalNumberCharWidth
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.measureNumberTextWidth
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun ActivityLogPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = context.mainVm
    val vm = viewModel<ActivityLogVm>()

    val logCount by vm.logCountFlow.collectAsState()
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val resetKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(resetKey, list.itemCount > 0)
    val timeTextWidth = measureNumberTextWidth(MaterialTheme.typography.bodySmall)

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popBackStack()
                })
            },
            title = {
                Text(
                    text = "界面记录",
                    modifier = Modifier.noRippleClickable { resetKey.intValue++ },
                )
            },
            actions = {
                if (logCount > 0) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            mainVm.dialogFlow.waitResult(
                                title = "删除记录",
                                text = "确定删除所有界面记录?",
                                error = true,
                            )
                            DbSet.activityLogDao.deleteAll()
                            toast("删除成功")
                        })
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
                val actionLog = list[i] ?: return@items
                val lastActionLog = if (i > 0) list[i - 1] else null
                CompositionLocalProvider(
                    LocalNumberCharWidth provides timeTextWidth
                ) {
                    ActivityLogCard(i = i, actionLog = actionLog, lastActionLog = lastActionLog)
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (logCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                    EmptyText(text = "暂无记录")
                }
            }
        }
    }
}

@Composable
private fun ActivityLogCard(
    i: Int,
    actionLog: ActivityLog,
    lastActionLog: ActivityLog?,
) {
    val mainVm = LocalMainViewModel.current
    val isDiffApp = actionLog.appId != lastActionLog?.appId
    val verticalPadding = if (i == 0) 0.dp else if (isDiffApp) 12.dp else 8.dp
    val showActivityId = actionLog.showActivityId
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = itemHorizontalPadding,
                end = itemHorizontalPadding,
                top = verticalPadding
            )
    ) {
        if (isDiffApp) {
            AppNameText(appId = actionLog.appId)
        }
        Row(
            modifier = Modifier
                .clickable(
                    onClick = {
                        mainVm.textFlow.value = listOfNotNull(
                            appInfoMapFlow.value[actionLog.appId]?.name,
                            actionLog.appId,
                            actionLog.showActivityId,
                        ).joinToString("\n")
                    },
                )
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Spacer(modifier = Modifier.width(2.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                FixedTimeText(
                    text = actionLog.date,
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