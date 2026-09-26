package li.gkd.app.ui.home

import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.data.AppInfo
import li.gkd.app.permission.PermissionStates
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.EditBlockAppListRoute
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.appItemPadding
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.getUpDownTransform
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.icon.GkBlockCloseIconButton
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppIcon
import li.gkd.app.ui.component.GkRuleStats
import li.gkd.app.ui.component.GkRuleStatsData
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkCheckbox
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMenuGroupCard
import li.gkd.app.ui.component.GkMenuItemCheckbox
import li.gkd.app.ui.component.GkMenuItemRadioButton
import li.gkd.app.ui.component.GkQueryPkgAuthCard
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState

@Composable
fun useAppListPage(): ScaffoldExt {
    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity

    val vm = viewModel { AppListVm(mainVm) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val appInfos = state.appInfos
    val searchStr = state.searchText
    val ruleSummary = state.ruleSummary

    val showSearchBar = state.showSearchBar
    val refreshing = state.refreshing
    val pullToRefreshState = rememberPullToRefreshState()
    val editWhiteListMode = state.editWhiteListMode
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnListChange(
        appInfos,
        key = { it.id },
        leadingItemKey = if (state.canQueryPackages) null else 1,
    )
    ResetPageScrollOnRequest(BottomNavItem.AppList, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.AppList,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    vm.onLeaveScreen()
                }
            }
            GkTopAppBar(scrollBehavior = scrollBehavior, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.imeController.requestHide()) {
                            vm.closeSearch()
                        }
                    }
                    GkAppBarTextField(
                        value = searchStr,
                        onValueChange = vm::setSearchText,
                        hint = UiStrings.app_name_id_input_hint,
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    val titleModifier = Modifier
                        .noRippleClickable(
                            onClick = throttle {
                                pageScrollState.resetScroll()
                            }
                        )
                    if (editWhiteListMode) {
                        BackHandler(onBack = vm::closeEditWhiteListMode)
                    }
                    AnimatedContent(
                        targetState = editWhiteListMode,
                        transitionSpec = { getUpDownTransform() },
                    ) { localEditWhiteListMode ->
                        if (localEditWhiteListMode) {
                            Text(
                                modifier = titleModifier,
                                text = UiStrings.app_whitelist,
                            )
                        } else {
                            Text(
                                modifier = titleModifier,
                                text = BottomNavItem.AppList.label,
                            )
                        }
                    }
                }
            }, actions = {
                if (state.queryPackagesAbnormal) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                        GkIconButton(
                            imageVector = GkIcons.WarningAmber,
                            contentDescription = PermissionStates.queryPackages.name + UiStrings.error_label,
                            onClick = throttle(vm.scope.launchUiAction {
                                mainVm.dialogRequests.showMessage(
                                    title = UiStrings.permission_error,
                                    text = UiStrings.app_list_permission_error_description(PermissionStates.queryPackages.name)
                                )
                            }),
                        )
                    }
                }
                GkSearchCloseIconButton(
                    onClick = throttle(vm::toggleSearch),
                    isSearchOpen = showSearchBar,
                    contentDescription = if (showSearchBar) UiStrings.search_close else UiStrings.app_list_search,
                )
                var expanded by remember { mutableStateOf(false) }
                GkFilterIconButton(
                    filtered = !state.showAllApps,
                    contentDescription = UiStrings.sort_filter,
                    onClick = {
                        expanded = true
                    }
                )
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                        ) {
                        GkMenuGroupCard(inTop = true, title = UiStrings.sort_title) {
                            AppSortOption.objects.forEach { option ->
                                GkMenuItemRadioButton(
                                    text = option.label,
                                    selected = AppSortOption.objects.findOption(store.appSort) == option,
                                    onClick = { vm.setSortType(option) },
                                )
                            }
                        }
                        GkMenuGroupCard(title = UiStrings.group_title) {
                            AppGroupOption.normalObjects.forEach { option ->
                                val newValue = option.invert(store.appGroupType)
                                GkMenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = option.label,
                                    checked = option.include(store.appGroupType),
                                    onClick = { vm.setAppGroupType(newValue) },
                                )
                            }
                        }
                        GkMenuGroupCard(title = UiStrings.filter_title) {
                            GkMenuItemCheckbox(
                                text = UiStrings.whitelist_title,
                                checked = store.showBlockApp,
                                onClick = {
                                    vm.setShowBlockApp(!store.showBlockApp)
                                },
                            )
                        }
                    }
                }
                GkBlockCloseIconButton(
                    isClose = editWhiteListMode,
                    contentDescription = UiStrings.whitelist_edit_mode_toggle,
                    onClickLabel = if (editWhiteListMode) UiStrings.edit_exit else UiStrings.edit_enter,
                    onClick = vm::toggleEditWhiteListMode,
                )
            })
        },
        floatingActionButton = {
            GkAnimatedFloatingActionButton(
                visible = editWhiteListMode,
                contentDescription = UiStrings.whitelist_edit,
                onClick = {
                    mainVm.navigatePage(EditBlockAppListRoute)
                },
                imageVector = GkIcons.Edit,
            )
        }
    ) { contentPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                if (!state.canQueryPackages) {
                    item(key = 1, contentType = 1) {
                        GkQueryPkgAuthCard()
                    }
                }
                items(appInfos, { it.id }) { appInfo ->
                    val stats = if (editWhiteListMode) null else GkRuleStatsData(
                        globalGroups = ruleSummary.appIdToGlobalGroupCount[appInfo.id] ?: 0,
                        appGroups = ruleSummary.appIdToAllGroups[appInfo.id]?.count { it.enable } ?: 0,
                        enabledOnly = true,
                    )
                    AppItemCard(
                        appInfo = appInfo,
                        stats = stats,
                        editWhiteListMode = editWhiteListMode,
                        inWhiteList = appInfo.id in state.whiteListAppIds,
                        onClick = {
                            if (editWhiteListMode) {
                                vm.toggleWhiteList(appInfo.id)
                            } else {
                                context.imeController.requestHide()
                                mainVm.navigatePage(AppConfigRoute(appInfo.id))
                            }
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        GkEmptyState(text = if (state.showAllApps) UiStrings.search_no_results else UiStrings.search_no_results_filter_hint)
                        GkPageBottomSpace(height = GkPageBottomSpaceDefaults.CompactHeight)
                    } else {
                        GkPageBottomSpace()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemCard(
    appInfo: AppInfo,
    stats: GkRuleStatsData?,
    editWhiteListMode: Boolean,
    inWhiteList: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = throttle(onClick))
            .clearAndSetSemantics {
                contentDescription = if (editWhiteListMode) {
                    appInfo.name
                } else {
                    UiStrings.app_whitelist_state_description(appInfo.name, stats?.takeIf { it.hasRules }?.description ?: appInfo.id)
                }
                if (inWhiteList) {
                    stateDescription = UiStrings.whitelist_member
                } else if (editWhiteListMode) {
                    stateDescription = UiStrings.whitelist_not_member
                }
                onClick(
                    label = if (editWhiteListMode) if (inWhiteList) UiStrings.whitelist_remove else UiStrings.whitelist_add else UiStrings.rule_summary_open,
                    action = null
                )
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkAppIcon(appId = appInfo.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            GkAppNameText(appInfo = appInfo)
            if (stats != null) {
                GkRuleStats(stats, emptyText = appInfo.id)
            } else {
                Text(
                    text = appInfo.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
        if (editWhiteListMode) {
            GkCheckbox(
                key = appInfo.id,
                checked = inWhiteList,
            )
        } else if (inWhiteList) {
            GkIcon(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp),
                imageVector = GkIcons.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
