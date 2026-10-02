package li.gkd.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_delete
import li.gkd.app.resources.data_load_failed
import li.gkd.app.resources.enable_anyway
import li.gkd.app.resources.loading_progress
import li.gkd.app.resources.selected_subscriptions_changed
import li.gkd.app.resources.subscription_add
import li.gkd.app.resources.subscription_add_dialog_open
import li.gkd.app.resources.subscription_battery_help
import li.gkd.app.resources.subscription_battery_warning
import li.gkd.app.resources.subscription_battery_warning_setting
import li.gkd.app.resources.subscription_delete
import li.gkd.app.resources.subscription_delete_except_local
import li.gkd.app.resources.subscription_multiple_enabled_warning
import li.gkd.app.resources.subscription_refresh_wait
import li.gkd.app.resources.subscription_refresh_wait_compact
import li.gkd.app.resources.subscription_settings
import li.gkd.app.resources.subscriptions_delete_confirmation
import li.gkd.app.resources.subscriptions_deleted_count
import li.gkd.app.resources.subscriptions_local_excluded_suffix
import li.gkd.app.resources.subscriptions_update
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.SubscriptionResult
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkDesktopKeyHandler
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMatchingBanner
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkSettingsDialog
import li.gkd.app.ui.component.GkSubsItemCard
import li.gkd.app.ui.component.GkSubscriptionActions
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.SubscriptionCardData
import li.gkd.app.ui.component.gkPageBottomSpace
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberPinnedListScrollState
import li.gkd.app.ui.component.rememberReorderSession
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.option.UpdateTimeOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.subscription.SubsManageUiState
import li.gkd.app.ui.subscription.message
import li.gkd.app.ui.text.formatTimeAgo
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.text.subscriptionMessage
import li.gkd.app.ui.text.subscriptionMessageResource
import li.gkd.db.LOCAL_SUBS_ID
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class SubsManageHost(
    val appName: String,
    val navigate: (AppRoute) -> Unit,
    val openSubscription: (Long) -> Unit,
    val requestUrl: suspend () -> String?,
    val confirm: suspend (title: String, text: String, error: Boolean) -> Boolean,
    val toast: (String) -> Unit,
    val openPowerHelp: () -> Unit,
)

@Composable
fun subsManagePage(
    vm: HomeViewModel,
    homeState: HomeState,
    host: SubsManageHost,
    selectionBackHandler: @Composable (Boolean, () -> Unit) -> Unit,
): ScaffoldExt {
    val loadableState by vm.subscriptionsState.collectAsStateWithLifecycle()
    return subsManageContent(vm, loadableState, homeState, host, selectionBackHandler)
}

@Composable
private fun subsManageContent(
    vm: HomeViewModel,
    loadableState: Loadable<SubsManageUiState>,
    homeState: HomeState,
    host: SubsManageHost,
    selectionBackHandler: @Composable (Boolean, () -> Unit) -> Unit,
): ScaffoldExt {

    val state = loadableState.value
    val toast = host.toast
    fun launchAction(block: suspend () -> Unit) = vm.scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast(e.subscriptionMessage())
        }
    }

    val refresh: () -> Unit = { launchAction { vm.refreshSubscriptions().message()?.let(toast) } }
    GkDesktopKeyHandler(Key.F5, onKey = refresh)
    var settingsDialogVisible by remember { mutableStateOf(false) }
    val powerWarningItem by vm.powerWarningItemFlow.collectAsStateWithLifecycle()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val subItems = state?.subItems.orEmpty()
    val subsIdToRaw = state?.subscriptions.orEmpty()
    val scope = vm.scope
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()

    val refreshing by li.gkd.app.subscription.SubscriptionRepository.updating.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    // 多选仅属于当前订阅 Tab 的临时交互状态，切换 Tab 后按设计清空，不要改为可保存状态。
    val selectionState = rememberMultiSelectionState<Long>()
    val allIds = remember(subItems) { subItems.mapTo(mutableSetOf()) { it.id } }
    val selectedIds = selectionState.selectedKeys intersect allIds
    val isSelectedMode = selectionState.active
    val reorderSession = rememberReorderSession(subItems) { it.id }
    val orderSubItems = reorderSession.items
    selectionBackHandler(isSelectedMode) {
        selectionState.clear()
    }
    LaunchedEffect(allIds) {
        selectionState.retain(allIds)
    }

    if (settingsDialogVisible) {
        GkSettingsDialog(
            onDismissRequest = { settingsDialogVisible = false },
            title = stringResource(Res.string.subscription_settings),
        ) {
            GkTextMenu(
                title = stringResource(Res.string.subscriptions_update),
                option = UpdateTimeOption.objects.findOption(store.updateSubsInterval),
                onOptionChange = { vm.setUpdateInterval(it.value) },
            )
            GkTextSwitch(
                title = stringResource(Res.string.subscription_battery_warning),
                subtitle = stringResource(Res.string.subscription_battery_warning_setting),
                checked = store.subsPowerWarn,
                onCheckedChange = vm::setPowerWarningEnabled,
            )
        }
    }

    powerWarningItem?.let { item ->
        GkAlertDialog(
            title = { Text(text = stringResource(Res.string.subscription_battery_warning)) },
            text = {
                Column {
                    Text(text = stringResource(Res.string.subscription_multiple_enabled_warning))
                    Text(
                        text = stringResource(Res.string.subscription_battery_help),
                        modifier = Modifier.clickable(onClick = {
                            vm.dismissPowerWarning()
                            host.openPowerHelp()
                        }),
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            onDismissRequest = {},
            confirmButton = {
                TextButton(
                    onClick = { launchAction { vm.confirmPowerWarning() } },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(Res.string.enable_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPowerWarning) {
                    Text(text = stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    val pageScrollState = rememberPinnedListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val lazyListState = pageScrollState.listState
    ResetPageScrollOnRequest(
        homeState,
        BottomNavItem.SubsManage,
        pageScrollState::resetScrollAndAwait
    )
    return ScaffoldExt(
        navItem = BottomNavItem.SubsManage,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = isSelectedMode,
                selectedCount = selectedIds.size,
                onExitSelection = selectionState::clear,
                scrollBehavior = scrollBehavior,
                title = {
                    Text(text = BottomNavItem.SubsManage.label)
                },
                actions = { selectedMode ->
                    if (selectedMode) {
                        GkMultiSelectionActions(
                            selectionState = selectionState,
                            keys = allIds,
                            enabled = !batchBusy && !refreshing && !reorderSession.dragging,
                        ) { dismiss ->
                            val canDeleteIds = selectedIds - LOCAL_SUBS_ID
                            GkBatchActionMenuItem(
                                text = if (canDeleteIds.isEmpty()) stringResource(Res.string.subscription_delete_except_local) else stringResource(
                                    Res.string.action_delete
                                ),
                                enabled = canDeleteIds.isNotEmpty(),
                                onDismiss = dismiss,
                                onClick = {
                                    val idsToDelete = canDeleteIds
                                    val text = Res.string.subscriptions_delete_confirmation.getSync(
                                        idsToDelete.size
                                    ) +
                                            if (LOCAL_SUBS_ID in selectedIds) Res.string.subscriptions_local_excluded_suffix.getSync() else ""
                                    launchAction {
                                        vm.runBatchAction {
                                            if (!host.confirm(
                                                    Res.string.subscription_delete.getSync(),
                                                    text,
                                                    true,
                                                )
                                            ) return@runBatchAction
                                            val result = vm.deleteSubscriptions(idsToDelete)
                                            if (result is SubscriptionResult.Success) {
                                                selectionState.removeDeleted(idsToDelete)
                                                toast(
                                                    if (result.count > 0) Res.string.subscriptions_deleted_count.getSync(
                                                        result.count
                                                    ) else Res.string.selected_subscriptions_changed.getSync()
                                                )
                                            } else {
                                                result.message()?.let { toast(it) }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        GkSubscriptionActions(
                            matching = store.enableMatch,
                            onToggleMatching = vm::toggleMatching,
                            onSettings = { settingsDialogVisible = true },
                            onMenuOpen = {
                                if (refreshing) toast(Res.string.subscription_refresh_wait.getSync())
                                !refreshing
                            },
                            onAddAppRule = {
                                host.navigate(
                                    UpsertRuleGroupRoute(
                                        subsId = LOCAL_SUBS_ID,
                                        groupKey = null,
                                        appId = "",
                                        forward = true
                                    )
                                )
                            },
                            onAddGlobalRule = {
                                host.navigate(
                                    UpsertRuleGroupRoute(
                                        subsId = LOCAL_SUBS_ID,
                                        groupKey = null,
                                        appId = null,
                                        forward = true
                                    )
                                )
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            GkAnimatedFloatingActionButton(
                contentDescription = stringResource(Res.string.subscription_add),
                onClickLabel = stringResource(Res.string.subscription_add_dialog_open),
                visible = !isSelectedMode,
                onClick = {
                    if (refreshing) {
                        toast(Res.string.subscription_refresh_wait_compact.getSync())
                    } else {
                        launchAction {
                            val url = host.requestUrl() ?: return@launchAction
                            vm.addOrModifySubscription(url).message()?.let { toast(it) }
                        }
                    }
                },
                imageVector = GkIcons.Add,
            )
        },
    ) { contentPadding ->
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                reorderSession.move(from.index, to.index)
            }
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            GkMatchingBanner(store.enableMatch, vm::enableMatching)
            PullToRefreshBox(
                modifier = Modifier.weight(1f),
                state = pullToRefreshState,
                isRefreshing = refreshing,
                onRefresh = refresh,
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (loadableState !is Loadable.Ready) {
                        item("load-state") {
                            Text(
                                text = (loadableState as? Loadable.Failure)?.cause?.subscriptionMessageResource()
                                    ?: stringResource(if (loadableState is Loadable.Loading) Res.string.loading_progress else Res.string.data_load_failed),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                        // Keep the initial gesture alive after it enters selection mode.
                        val canDrag = reorderSession.dragging ||
                                (!refreshing && !batchBusy && !isSelectedMode && orderSubItems.size > 1)
                        ReorderableItem(
                            state = reorderableLazyColumnState,
                            key = subItem.id,
                            enabled = canDrag,
                        ) {
                            val interactionSource = remember { MutableInteractionSource() }
                            GkSubsItemCard(
                                modifier = Modifier.longPressDraggableHandle(
                                    enabled = canDrag,
                                    interactionSource = interactionSource,
                                    onDragStarted = {
                                        reorderSession.startDragging()
                                        if (!isSelectedMode) {
                                            selectionState.select(subItem.id)
                                        }
                                    },
                                    onDragStopped = {
                                        val result = reorderSession.finishDragging()
                                        if (result.moved) {
                                            selectionState.clear()
                                        }
                                        result.reorderedItems?.let { reorderedItems ->
                                            val changedItems =
                                                reorderedItems.mapIndexedNotNull { index, item ->
                                                    item.copy(order = index)
                                                        .takeIf { it.order != item.order }
                                                }
                                            if (changedItems.isNotEmpty()) {
                                                launchAction { vm.updateOrder(changedItems) }
                                            }
                                        }
                                    },
                                ),
                                interactionSource = interactionSource,
                                data = SubscriptionCardData(
                                    id = subItem.id,
                                    enabled = subItem.enable,
                                    name = subsIdToRaw[subItem.id]?.name,
                                    version = subsIdToRaw[subItem.id]?.version ?: 0,
                                    author = subsIdToRaw[subItem.id]?.author,
                                    globalGroups = subsIdToRaw[subItem.id]?.globalGroups?.size ?: 0,
                                    apps = subsIdToRaw[subItem.id]?.apps?.size ?: 0,
                                    appGroups = subsIdToRaw[subItem.id]?.appGroups?.size ?: 0,
                                    updatedAt = formatTimeAgo(subItem.mtime),
                                    appName = host.appName,
                                    loadError = state?.loadErrors?.get(subItem.id)?.subscriptionMessageResource(),
                                    refreshError = state?.refreshErrors?.get(subItem.id)?.subscriptionMessageResource(),
                                ),
                                matchingEnabled = store.enableMatch,
                                index = index + 1,
                                isSelectedMode = isSelectedMode,
                                selectionEnabled = !batchBusy && !refreshing && !reorderSession.dragging,
                                handlesLongPress = !canDrag,
                                onSelect = {
                                    if (!batchBusy && !refreshing && !reorderSession.dragging) {
                                        selectionState.select(subItem.id)
                                    }
                                },
                                isSelected = selectedIds.contains(subItem.id),
                                refreshing = refreshing,
                                onOpen = {
                                    host.openSubscription(subItem.id)
                                },
                                onCheckedChange = { checked ->
                                    launchAction { vm.requestSubscriptionEnabled(subItem, checked) }
                                },
                                onSelectedChange = { selectionState.toggle(subItem.id) },
                            )
                        }
                    }
                    gkPageBottomSpace()
                }
            }
        }
    }
}
