package li.songe.gkd.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.MainActivity
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.Tuple3
import li.songe.gkd.data.stringify
import li.songe.gkd.data.switch
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.FixedTimeText
import li.songe.gkd.ui.component.LocalNumberCharWidth
import li.songe.gkd.ui.component.StartEllipsisText
import li.songe.gkd.ui.component.measureNumberTextWidth
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun ActionLogPage() {
    val context = LocalContext.current as MainActivity
    val mainVm = context.mainVm
    val navController = LocalNavController.current
    val vm = viewModel<ActionLogVm>()
    val actionLogCount by vm.actionLogCountFlow.collectAsState()
    val actionDataItems = vm.pagingDataFlow.collectAsLazyPagingItems()

    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var previewActionLog by remember {
        mutableStateOf<ActionLog?>(null)
    }
    val (previewConfigFlow, setPreviewConfigFlow) = remember {
        mutableStateOf<StateFlow<SubsConfig?>>(MutableStateFlow(null))
    }
    LaunchedEffect(key1 = previewActionLog, block = {
        val log = previewActionLog
        if (log != null) {
            val stateFlow = (if (log.groupType == SubsConfig.AppGroupType) {
                DbSet.subsConfigDao.queryAppGroupTypeConfig(
                    log.subsId, log.appId, log.groupKey
                )
            } else {
                DbSet.subsConfigDao.queryGlobalGroupTypeConfig(log.subsId, log.groupKey)
            }).map { s -> s.firstOrNull() }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, null)
            setPreviewConfigFlow(stateFlow)
        } else {
            setPreviewConfigFlow(MutableStateFlow(null))
        }
    })
    val timeTextWidth = measureNumberTextWidth(MaterialTheme.typography.bodySmall)

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = throttle {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = { Text(text = "触发记录") },
            actions = {
                if (actionLogCount > 0) {
                    IconButton(onClick = throttle(fn = mainVm.viewModelScope.launchAsFn {
                        mainVm.dialogFlow.waitResult(
                            title = "删除记录",
                            text = "确定删除所有触发记录?",
                            error = true,
                        )
                        DbSet.actionLogDao.deleteAll()
                    })) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
        ) {
            items(
                count = actionDataItems.itemCount,
                key = actionDataItems.itemKey { c -> c.t0.id }
            ) { i ->
                val item = actionDataItems[i] ?: return@items
                val lastItem = if (i > 0) actionDataItems[i - 1] else null
                CompositionLocalProvider(
                    LocalNumberCharWidth provides timeTextWidth
                ) {
                    ActionLogCard(
                        i = i,
                        item = item,
                        lastItem = lastItem,
                        onClick = {
                            previewActionLog = item.t0
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (actionLogCount == 0 && actionDataItems.loadState.refresh !is LoadState.Loading) {
                    EmptyText(text = "暂无记录")
                }
            }
        }
    })

    previewActionLog?.let { clickLog ->
        Dialog(onDismissRequest = { previewActionLog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val appInfoCache by appInfoCacheFlow.collectAsState()
                val previewConfig = previewConfigFlow.collectAsState().value
                val oldExclude = remember(key1 = previewConfig?.exclude) {
                    ExcludeData.parse(previewConfig?.exclude)
                }
                val appInfo = appInfoCache[clickLog.appId]

                Text(
                    text = "查看规则组", modifier = Modifier
                        .clickable(onClick = throttle {
                            if (clickLog.groupType == SubsConfig.AppGroupType) {
                                navController
                                    .toDestinationsNavigator()
                                    .navigate(
                                        AppItemPageDestination(
                                            clickLog.subsId, clickLog.appId, clickLog.groupKey
                                        )
                                    )
                            } else if (clickLog.groupType == SubsConfig.GlobalGroupType) {
                                navController
                                    .toDestinationsNavigator()
                                    .navigate(
                                        GlobalRulePageDestination(
                                            clickLog.subsId, clickLog.groupKey
                                        )
                                    )
                            }
                            previewActionLog = null
                        })
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                if (clickLog.groupType == SubsConfig.GlobalGroupType) {
                    val group =
                        subsIdToRaw[clickLog.subsId]?.globalGroups?.find { g -> g.key == clickLog.groupKey }
                    val appChecked = if (group != null) {
                        getChecked(
                            oldExclude,
                            group,
                            clickLog.appId,
                            appInfo
                        )
                    } else {
                        null
                    }
                    if (appChecked != null) {
                        Text(
                            text = if (appChecked) "在此应用禁用" else "移除在此应用的禁用",
                            modifier = Modifier
                                .clickable(
                                    onClick = vm.viewModelScope.launchAsFn(
                                        Dispatchers.IO
                                    ) {
                                        val subsConfig = previewConfig ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = clickLog.subsId,
                                            groupKey = clickLog.groupKey,
                                        )
                                        val newSubsConfig = subsConfig.copy(
                                            exclude = oldExclude
                                                .copy(
                                                    appIds = oldExclude.appIds
                                                        .toMutableMap()
                                                        .apply {
                                                            set(clickLog.appId, appChecked)
                                                        })
                                                .stringify()
                                        )
                                        DbSet.subsConfigDao.insert(newSubsConfig)
                                        toast("更新禁用")
                                    })
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                }
                if (clickLog.activityId != null) {
                    val disabled =
                        oldExclude.activityIds.contains(clickLog.appId to clickLog.activityId)
                    Text(
                        text = if (disabled) "移除在此页面的禁用" else "在此页面禁用",
                        modifier = Modifier
                            .clickable(onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                val subsConfig =
                                    if (clickLog.groupType == SubsConfig.AppGroupType) {
                                        previewConfig ?: SubsConfig(
                                            type = SubsConfig.AppGroupType,
                                            subsItemId = clickLog.subsId,
                                            appId = clickLog.appId,
                                            groupKey = clickLog.groupKey,
                                        )
                                    } else {
                                        previewConfig ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = clickLog.subsId,
                                            groupKey = clickLog.groupKey,
                                        )
                                    }
                                val newSubsConfig = subsConfig.copy(
                                    exclude = oldExclude
                                        .switch(
                                            clickLog.appId,
                                            clickLog.activityId
                                        )
                                        .stringify()
                                )
                                DbSet.subsConfigDao.insert(newSubsConfig)
                                toast("更新禁用")
                            })
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }

                Text(
                    text = "删除记录",
                    modifier = Modifier
                        .clickable(onClick = vm.viewModelScope.launchAsFn {
                            previewActionLog = null
                            DbSet.actionLogDao.delete(clickLog)
                            toast("删除成功")
                        })
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


@Composable
private fun ActionLogCard(
    i: Int,
    item: Tuple3<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>,
    lastItem: Tuple3<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>?,
    onClick: () -> Unit
) {
    val context = LocalContext.current as MainActivity
    val (actionLog, group, rule) = item
    val lastActionLog = lastItem?.t0
    val isDiffApp = actionLog.appId != lastActionLog?.appId
    val verticalPadding = if (i == 0) 0.dp else if (isDiffApp) 12.dp else 8.dp
    val subsIdToRaw by subsIdToRawFlow.collectAsState()
    val subscription = subsIdToRaw[actionLog.subsId]
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
                .clickable(onClick = onClick)
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
                    val showActivityId = actionLog.showActivityId
                    if (showActivityId != null) {
                        StartEllipsisText(
                            text = showActivityId,
                            modifier = Modifier.height(LocalTextStyle.current.lineHeight.value.dp),
                        )
                    } else {
                        Text(
                            text = "null",
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                    Text(
                        text = subscription?.name ?: "id=${actionLog.subsId}",
                        modifier = Modifier.clickable(onClick = throttle {
                            if (subsItemsFlow.value.any { it.id == actionLog.subsId }) {
                                context.mainVm.sheetSubsIdFlow.value = actionLog.subsId
                            } else {
                                toast("订阅不存在")
                            }
                        })
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val groupDesc = group?.name.toString()
                        if (actionLog.groupType == SubsConfig.GlobalGroupType) {
                            Icon(
                                imageVector = Icons.Default.SportsBasketball,
                                contentDescription = null,
                                modifier = Modifier
                                    .clickable(onClick = throttle {
                                        toast("${group?.name ?: "当前规则组"} 是全局规则组")
                                    })
                                    .size(LocalTextStyle.current.lineHeight.value.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = groupDesc,
                            color = LocalContentColor.current.let {
                                if (group?.name == null) it.copy(alpha = 0.5f) else it
                            },
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

