package li.songe.gkd.ui.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.generated.destinations.SlowGroupPageDestination
import com.ramcosta.composedestinations.generated.destinations.UpsertRuleGroupPageDestination
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.data.Value
import li.songe.gkd.data.importData
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.store.switchStoreEnableMatch
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.component.TextMenu
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.UpdateTimeOption
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.deleteSubscription
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsMapFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubsMutex
import li.songe.gkd.util.usedSubsEntriesFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current

    val vm = viewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsMapFlow.collectAsState()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    val refreshing by updateSubsMutex.state.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isSelectedMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val draggedFlag = remember { Value(false) }
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }
    BackHandler(isSelectedMode) {
        isSelectedMode = false
    }
    LaunchedEffect(key1 = subItems.size) {
        if (subItems.size <= 1) {
            isSelectedMode = false
        }
    }

    var showSettingsDlg by remember { mutableStateOf(false) }
    if (showSettingsDlg) {
        AlertDialog(
            onDismissRequest = { showSettingsDlg = false },
            title = { Text("订阅设置") },
            text = {
                val store by storeFlow.collectAsState()
                Column {
                    TextMenu(
                        modifier = Modifier.padding(0.dp, itemVerticalPadding),
                        title = "更新订阅",
                        option = UpdateTimeOption.objects.findOption(store.updateSubsInterval)
                    ) {
                        storeFlow.update { s -> s.copy(updateSubsInterval = it.value) }
                    }
                    val updateValue = throttle {
                        storeFlow.update { it.copy(subsPowerWarn = !it.subsPowerWarn) }
                    }
                    Row(
                        modifier = Modifier
                            .padding(0.dp, itemVerticalPadding)
                            .clickable(
                                onClickLabel = if (store.subsPowerWarn) "关闭警告" else "开启警告",
                                onClick = updateValue
                            )
                            .semantics(mergeDescendants = true) {
                                stateDescription = if (store.subsPowerWarn) "已开启" else "已关闭"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "耗电警告",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "启用多条订阅时弹窗确认",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Checkbox(
                            checked = store.subsPowerWarn,
                            onCheckedChange = null,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDlg = false }, modifier = Modifier.semantics {
                    onClick(label = "关闭弹窗", action = null)
                }) {
                    Text("关闭")
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    return ScaffoldExt(
        navItem = BottomNavItem.SubsManage,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    PerfIconButton(
                        imageVector = PerfIcon.Close,
                        contentDescription = "取消选择",
                        onClick = { isSelectedMode = false },
                    )
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedIds.isNotEmpty()) selectedIds.size.toString() else "",
                    )
                } else {
                    Text(
                        text = BottomNavItem.SubsManage.label,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                AnimatedContent(
                    targetState = isSelectedMode,
                    transitionSpec = { getUpDownTransform() },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Row {
                        if (it) {
                            PerfIconButton(
                                imageVector = PerfIcon.Share,
                                contentDescription = "分享选中订阅",
                                onClick = {
                                    mainVm.showShareDataIdsFlow.value = selectedIds
                                })
                            val canDeleteIds = if (selectedIds.contains(LOCAL_SUBS_ID)) {
                                selectedIds - LOCAL_SUBS_ID
                            } else {
                                selectedIds
                            }
                            if (canDeleteIds.isNotEmpty()) {
                                val text = "确定删除所选 ${canDeleteIds.size} 个订阅?".let { s ->
                                    if (selectedIds.contains(LOCAL_SUBS_ID)) "$s\n\n注: 不包含本地订阅" else s
                                }
                                PerfIconButton(
                                    imageVector = PerfIcon.Delete,
                                    contentDescription = "删除选中订阅",
                                    onClick = vm.viewModelScope.launchAsFn {
                                        mainVm.dialogFlow.waitResult(
                                            title = "删除订阅",
                                            text = text,
                                            error = true,
                                        )
                                        deleteSubscription(*canDeleteIds.toLongArray())
                                        selectedIds = selectedIds - canDeleteIds
                                        if (selectedIds.size == canDeleteIds.size) {
                                            isSelectedMode = false
                                        }
                                    },
                                )
                            }
                        } else {
                            val ruleSummary by ruleSummaryFlow.collectAsState()
                            AnimatedVisibility(
                                visible = ruleSummary.slowGroupCount > 0,
                                enter = scaleIn(),
                                exit = scaleOut(),
                            ) {
                                PerfIconButton(
                                    imageVector = PerfIcon.Eco,
                                    contentDescription = "缓慢查询规则列表",
                                    onClickLabel = "查看列表",
                                    onClick = throttle {
                                        mainVm.navigatePage(SlowGroupPageDestination)
                                    })
                            }
                            val scope = rememberCoroutineScope()
                            val enableMatch by remember {
                                storeFlow.mapState(scope) { s -> s.enableMatch }
                            }.collectAsState()
                            PerfIconButton(
                                id = if (enableMatch) SafeR.ic_flash_on else SafeR.ic_flash_off,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (!enableMatch) {
                                        CheckboxDefaults.colors().checkedBoxColor
                                    } else {
                                        LocalContentColor.current
                                    }
                                ),
                                contentDescription = "规则匹配" + if (enableMatch) "已启用" else "已禁用",
                                onClickLabel = "切换开关",
                                onClick = throttle { switchStoreEnableMatch() },
                            )
                            PerfIconButton(
                                id = SafeR.ic_page_info,
                                contentDescription = "订阅设置",
                                onClickLabel = "打开设置弹窗",
                                onClick = {
                                    showSettingsDlg = true
                                })
                        }
                    }
                }
                PerfIconButton(
                    imageVector = PerfIcon.MoreVert,
                    contentDescription = "更多操作",
                    onClick = {
                        if (updateSubsMutex.mutex.isLocked) {
                            toast("正在刷新订阅，请稍后操作")
                        } else {
                            expanded = true
                        }
                    })
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    key(isSelectedMode) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (isSelectedMode) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "全选")
                                    },
                                    onClick = {
                                        expanded = false
                                        selectedIds = subItems.map { it.id }.toSet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "反选")
                                    },
                                    onClick = {
                                        expanded = false
                                        val newSelectedIds =
                                            subItems.map { it.id }.toSet() - selectedIds
                                        if (newSelectedIds.isEmpty()) {
                                            isSelectedMode = false
                                        }
                                        selectedIds = newSelectedIds
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "导入本地数据")
                                    },
                                    onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                        expanded = false
                                        val result =
                                            context.launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "application/zip"
                                            })
                                        val uri = result.data?.data
                                        if (uri == null) {
                                            toast("未选择文件")
                                            return@launchAsFn
                                        }
                                        importData(uri)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "添加应用规则") },
                                    onClick = throttle {
                                        expanded = false
                                        mainVm.navigatePage(
                                            UpsertRuleGroupPageDestination(
                                                subsId = LOCAL_SUBS_ID,
                                                groupKey = null,
                                                appId = "",
                                                forward = true,
                                            )
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "添加全局规则") },
                                    onClick = throttle {
                                        expanded = false
                                        mainVm.navigatePage(
                                            UpsertRuleGroupPageDestination(
                                                subsId = LOCAL_SUBS_ID,
                                                groupKey = null,
                                                appId = null,
                                                forward = true,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                contentDescription = "添加订阅",
                onClickLabel = "打开添加订阅弹窗",
                visible = !isSelectedMode,
                onClick = {
                    if (updateSubsMutex.mutex.isLocked) {
                        toast("正在刷新订阅,请稍后操作")
                    } else {
                        mainVm.viewModelScope.launchTry {
                            val url = mainVm.inputSubsLinkOption.getResult() ?: return@launchTry
                            mainVm.addOrModifySubs(url)
                        }
                    }
                }
            ) {
                PerfIcon(
                    imageVector = PerfIcon.Add,
                )
            }
        },
    ) { contentPadding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }
                draggedFlag.value = true
            }
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = { checkSubsUpdate(true) }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                    val canDrag = !refreshing && orderSubItems.size > 1
                    ReorderableItem(
                        state = reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = canDrag,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        SubsItemCard(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        isSelectedMode = true
                                        selectedIds = setOf(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    if (draggedFlag.value) {
                                        draggedFlag.value = false
                                        isSelectedMode = false
                                        selectedIds = emptySet()
                                    }
                                    val changeItems = orderSubItems.filter { newItem ->
                                        subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                    }
                                    if (changeItems.isNotEmpty()) {
                                        vm.viewModelScope.launchTry {
                                            DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                        }
                                    }
                                },
                            ),
                            interactionSource = interactionSource,
                            subsItem = subItem,
                            subscription = subsIdToRaw[subItem.id],
                            index = index + 1,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            onCheckedChange = mainVm.viewModelScope.launchAsFn { checked ->
                                if (checked && storeFlow.value.subsPowerWarn && !subItem.isLocal && usedSubsEntriesFlow.value.any { !it.subsItem.isLocal }) {
                                    mainVm.dialogFlow.waitResult(
                                        title = "耗电警告",
                                        textContent = {
                                            Column {
                                                Text(text = "启用多个远程订阅可能导致执行大量重复规则, 这可能造成规则执行卡顿以及多余耗电\n\n请认真考虑后再确认开启！！！\n")
                                                Text(
                                                    text = "查看耗电说明",
                                                    modifier = Modifier.clickable(onClick = throttle {
                                                        mainVm.dialogFlow.value = null
                                                        mainVm.navigatePage(
                                                            WebViewPageDestination(
                                                                initUrl = ShortUrlSet.URL6
                                                            )
                                                        )
                                                    }),
                                                    textDecoration = TextDecoration.Underline,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        confirmText = "仍然启用",
                                        error = true
                                    )
                                }
                                DbSet.subsItemDao.updateEnable(subItem.id, checked)
                            },
                            onSelectedChange = {
                                val newSelectedIds = if (selectedIds.contains(subItem.id)) {
                                    selectedIds.toMutableSet().apply {
                                        remove(subItem.id)
                                    }
                                } else {
                                    selectedIds + subItem.id
                                }
                                selectedIds = newSelectedIds
                                if (newSelectedIds.isEmpty()) {
                                    isSelectedMode = false
                                }
                            },
                        )
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}
