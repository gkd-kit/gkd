package li.gkd.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.R
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.SlowGroupRoute
import li.gkd.app.ui.UpsertRuleGroupRoute
import li.gkd.app.ui.WebViewRoute
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SettingsDialog
import li.gkd.app.ui.component.SubsItemCard
import li.gkd.app.ui.component.TextMenu
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberReorderSession
import li.gkd.app.ui.component.rememberPinnedListScrollState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.util.LOCAL_SUBS_ID
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.SubscriptionResult
import li.gkd.app.util.UpdateTimeOption
import li.gkd.app.util.findOption
import li.gkd.app.util.getUpDownTransform
import li.gkd.app.util.launchTry
import li.gkd.app.util.ruleSummaryFlow
import li.gkd.app.util.throttle
import li.gkd.app.util.toast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val vm = viewModel<SubsManageVm>()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    return if (state == null) {
        subsManageStatePage(loadableState)
    } else {
        useLoadedSubsManagePage(vm, state)
    }
}

private fun subsManageStatePage(
    state: Loadable<SubsManageUiState>,
) = ScaffoldExt(
    navItem = BottomNavItem.SubsManage,
    content = { contentPadding ->
        val error = (state as? Loadable.Failure)?.cause
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = error?.message ?: if (error == null) "加载中..." else "数据加载失败",
                color = if (error == null) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    },
)

@Composable
private fun useLoadedSubsManagePage(
    vm: SubsManageVm,
    state: SubsManageUiState,
): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val settingsDialogVisible by vm.settingsDialogVisibleFlow.collectAsStateWithLifecycle()
    val powerWarningItem by vm.powerWarningItemFlow.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val ruleSummary by ruleSummaryFlow.collectAsStateWithLifecycle()
    val subItems = state.subItems
    val subsIdToRaw = state.subscriptions
    val scope = vm.scope

    val refreshing = state.refreshing
    val pullToRefreshState = rememberPullToRefreshState()
    // 多选仅属于当前订阅 Tab 的临时交互状态，切换 Tab 后按设计清空，不要改为可保存状态。
    val selectionState = rememberMultiSelectionState<Long>()
    val selectedIds = selectionState.selectedKeys
    val isSelectedMode = selectionState.active
    val reorderSession = rememberReorderSession(subItems) { it.id }
    val orderSubItems = reorderSession.items
    BackHandler(isSelectedMode) {
        selectionState.clear()
    }
    LaunchedEffect(subItems) {
        if (subItems.size <= 1) {
            selectionState.clear()
        } else {
            selectionState.retain(subItems.mapTo(mutableSetOf()) { it.id })
        }
    }

    if (settingsDialogVisible) {
        SettingsDialog(
            onDismissRequest = { vm.setSettingsDialogVisible(false) },
            title = "订阅设置",
        ) {
            TextMenu(
                title = "更新订阅",
                option = UpdateTimeOption.objects.findOption(store.updateSubsInterval),
                onOptionChange = { vm.setUpdateInterval(it.value) },
            )
            TextSwitch(
                title = "耗电警告",
                subtitle = "启用多条订阅时弹窗确认",
                checked = store.subsPowerWarn,
                onCheckedChange = throttle(fn = vm::setPowerWarningEnabled),
            )
        }
    }

    powerWarningItem?.let { item ->
        AppAlertDialog(
            title = { Text(text = "耗电警告") },
            text = {
                Column {
                    Text(text = "启用多个远程订阅可能导致执行大量重复规则, 这可能造成规则执行卡顿以及多余耗电\n\n请认真考虑后再确认开启！！！\n")
                    Text(
                        text = "查看耗电说明",
                        modifier = Modifier.clickable(onClick = throttle {
                            vm.dismissPowerWarning()
                            mainVm.navigatePage(WebViewRoute(initUrl = ShortUrlSet.URL6))
                        }),
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = throttle(vm::confirmPowerWarning),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = "仍然启用")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPowerWarning) {
                    Text(text = "取消")
                }
            },
        )
    }

    val pageScrollState = rememberPinnedListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val lazyListState = pageScrollState.listState
    ResetPageScrollOnRequest(BottomNavItem.SubsManage, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.SubsManage,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    PerfIconButton(
                        imageVector = PerfIcon.Close,
                        contentDescription = "取消选择",
                        onClick = selectionState::clear,
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
                                    onClick = {
                                        scope.launchTry {
                                            if (!mainVm.dialogRequests.confirm(
                                                title = "删除订阅",
                                                text = text,
                                                error = true,
                                            )) return@launchTry
                                            val result = vm.deleteSubscriptions(canDeleteIds)
                                            result.message?.let {
                                                toast(it)
                                            }
                                            if (result is SubscriptionResult.Success) {
                                                selectionState.selectAll(selectedIds - canDeleteIds)
                                            }
                                        }
                                    },
                                )
                            }
                        } else {
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
                                        mainVm.navigatePage(SlowGroupRoute)
                                    })
                            }
                            PerfIconButton(
                                id = if (store.enableMatch) R.drawable.ic_flash_on else R.drawable.ic_flash_off,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (!store.enableMatch) {
                                        CheckboxDefaults.colors().checkedBoxColor
                                    } else {
                                        LocalContentColor.current
                                    }
                                ),
                                contentDescription = "规则匹配" + if (store.enableMatch) "已启用" else "已禁用",
                                onClickLabel = "切换开关",
                                onClick = throttle(vm::toggleMatching),
                            )
                            PerfIconButton(
                                id = R.drawable.ic_page_info,
                                contentDescription = "订阅设置",
                                onClickLabel = "打开设置弹窗",
                                onClick = {
                                    vm.setSettingsDialogVisible(true)
                                })
                        }
                    }
                }
                PerfIconButton(
                    imageVector = PerfIcon.MoreVert,
                    contentDescription = "更多操作",
                    onClick = {
                        if (refreshing) {
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
                                        selectionState.selectAll(subItems.map { it.id })
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "反选")
                                    },
                                    onClick = {
                                        expanded = false
                                        selectionState.invert(subItems.map { it.id })
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(text = "添加应用规则") },
                                    onClick = throttle {
                                        expanded = false
                                        mainVm.navigatePage(
                                            UpsertRuleGroupRoute(
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
                                            UpsertRuleGroupRoute(
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
                    if (refreshing) {
                        toast("正在刷新订阅,请稍后操作")
                    } else {
                        scope.launchTry {
                            val url = mainVm.subsLinkDialog.request() ?: return@launchTry
                            vm.addOrModifySubscription(url).message?.let { toast(it) }
                        }
                    }
                },
                imageVector = PerfIcon.Add,
            )
        },
    ) { contentPadding ->
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                reorderSession.move(from.index, to.index)
            }
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
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
                                    reorderSession.startDragging()
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        selectionState.selectOnly(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    val result = reorderSession.finishDragging()
                                    if (result.moved) {
                                        selectionState.clear()
                                    }
                                    result.reorderedItems?.let { reorderedItems ->
                                        val changedItems = reorderedItems.mapIndexedNotNull { index, item ->
                                            item.copy(order = index).takeIf { it.order != item.order }
                                        }
                                        if (changedItems.isNotEmpty()) {
                                            vm.updateOrder(changedItems)
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
                            loadError = state.loadErrors[subItem.id],
                            refreshError = state.refreshErrors[subItem.id],
                            refreshing = refreshing,
                            onOpen = {
                                mainVm.subsSheet.show(subItem.id)
                            },
                            onCheckedChange = { checked ->
                                vm.requestSubscriptionEnabled(subItem, checked)
                            },
                            onSelectedChange = { selectionState.toggle(subItem.id) },
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
