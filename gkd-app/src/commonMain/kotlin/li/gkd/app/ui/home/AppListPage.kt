package li.gkd.app.ui.home

import li.gkd.app.app.AppInfoRepository

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import li.gkd.app.model.AppInfo
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_list_permission_error_description
import li.gkd.app.resources.app_list_search
import li.gkd.app.resources.app_list_update_success
import li.gkd.app.resources.app_name_id_input_hint
import li.gkd.app.resources.app_whitelist
import li.gkd.app.resources.app_whitelist_state_description
import li.gkd.app.resources.data_load_failed
import li.gkd.app.resources.edit_enter
import li.gkd.app.resources.edit_exit
import li.gkd.app.resources.filter_title
import li.gkd.app.resources.group_title
import li.gkd.app.resources.loading_progress
import li.gkd.app.resources.permission_error
import li.gkd.app.resources.permission_query_apps
import li.gkd.app.resources.rule_summary_open
import li.gkd.app.resources.search_close
import li.gkd.app.resources.search_no_results
import li.gkd.app.resources.search_no_results_filter_hint
import li.gkd.app.resources.sort_filter
import li.gkd.app.resources.sort_title
import li.gkd.app.resources.whitelist_add
import li.gkd.app.resources.whitelist_edit
import li.gkd.app.resources.whitelist_edit_mode_toggle
import li.gkd.app.resources.whitelist_member
import li.gkd.app.resources.whitelist_not_member
import li.gkd.app.resources.whitelist_remove
import li.gkd.app.resources.whitelist_title
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkCheckbox
import li.gkd.app.ui.component.GkDesktopKeyHandler
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMenuGroupCard
import li.gkd.app.ui.component.GkMenuItemCheckbox
import li.gkd.app.ui.component.GkMenuItemRadioButton
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkQueryPkgAuthCard
import li.gkd.app.ui.component.GkRuleStats
import li.gkd.app.ui.component.GkRuleStatsData
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.LocalOverlayBackHandler
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.icon.GkBlockCloseIconButton
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.EditBlockAppListRoute
import li.gkd.app.ui.navigation.GkAppIcon
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.hideIme
import li.gkd.app.ui.navigation.requestQueryPackages
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.style.getUpDownTransform
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.text.displayMessage
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

data class AppListContentState(
    val appInfos: List<AppInfo>,
    val listState: Loadable<Unit> = Loadable.Ready(Unit),
    val ruleStatsFailed: Boolean = false,
    val searchText: String = "",
    val showSearchBar: Boolean = false,
    val editWhiteListMode: Boolean = false,
    val showAllApps: Boolean = true,
    val ruleStats: Map<String, GkRuleStatsData> = emptyMap(),
    val whiteListAppIds: Set<String> = emptySet(),
    val canQueryPackages: Boolean = true,
    val queryPackagesAbnormal: Boolean = false,
    val refreshing: Boolean = false,
    val sort: Int = 0,
    val group: Int = 3,
    val showBlocked: Boolean = true,
)

@Composable
fun appListPage(
    window: AppWindow,
    vm: HomeViewModel,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit,
    dialogs: DialogRequests,
): ScaffoldExt {
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val appState by vm.appsState.collectAsStateWithLifecycle()
    val blocked by SettingsRepository.blockMatchAppList.collectAsStateWithLifecycle()
    val controls = rememberAppListPageState()
    val state = controls.content(appState, store, blocked)

    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            try {
                AppInfoRepository.refresh()
                toast(getString(Res.string.app_list_update_success))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.displayMessage())
            }
        }
    }
    GkDesktopKeyHandler(Key.F5, onKey = ::refresh)
    val appInfos = state.appInfos
    val searchStr = state.searchText

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
    ResetPageScrollOnRequest(
        vm.homeState,
        BottomNavItem.AppList,
        pageScrollState::resetScrollAndAwait,
    )
    return ScaffoldExt(
        navItem = BottomNavItem.AppList,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    controls.onLeave()
                }
            }
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        GkBackHandler { if (!window.hideIme()) controls.closeSearch() }
                        GkAppBarTextField(
                            value = searchStr,
                            onValueChange = controls::setSearchText,
                            hint = stringResource(Res.string.app_name_id_input_hint),
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        val titleModifier =
                            Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = {
                                    pageScrollState.resetScroll()
                                },
                            )
                        if (editWhiteListMode) {
                            GkBackHandler(onBack = controls::closeEdit)
                        }
                        AnimatedContent(
                            targetState = editWhiteListMode,
                            transitionSpec = { getUpDownTransform() },
                        ) { localEditWhiteListMode ->
                            if (localEditWhiteListMode) {
                                Text(
                                    modifier = titleModifier,
                                    text = stringResource(Res.string.app_whitelist),
                                )
                            } else {
                                Text(
                                    modifier = titleModifier,
                                    text = BottomNavItem.AppList.label,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (state.queryPackagesAbnormal) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.error
                        ) {
                            GkIconButton(
                                imageVector = GkIcons.WarningAmber,
                                contentDescription = stringResource(Res.string.permission_error),
                                onClick = {
                                    scope.launch {
                                        dialogs.showMessage(
                                            getString(Res.string.permission_error),
                                            getString(
                                                Res.string.app_list_permission_error_description,
                                                getString(Res.string.permission_query_apps),
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    GkSearchCloseIconButton(
                        onClick = controls::toggleSearch,
                        isSearchOpen = showSearchBar,
                        contentDescription =
                            if (showSearchBar) stringResource(Res.string.search_close)
                            else stringResource(Res.string.app_list_search),
                    )
                    var expanded by remember { mutableStateOf(false) }
                    if (expanded) LocalOverlayBackHandler.current { expanded = false }
                    GkFilterIconButton(
                        filtered = !state.showAllApps,
                        contentDescription = stringResource(Res.string.sort_filter),
                        onClick = {
                            expanded = true
                        },
                    )
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            GkMenuGroupCard(
                                inTop = true,
                                title = stringResource(Res.string.sort_title),
                            ) {
                                AppSortOption.objects.forEach { option ->
                                    GkMenuItemRadioButton(
                                        text = option.label,
                                        selected =
                                            AppSortOption.objects.findOption(state.sort) == option,
                                        onClick = { SettingsRepository.updateSettings { it.copy(appSort = option.value) } },
                                    )
                                }
                            }
                            GkMenuGroupCard(title = stringResource(Res.string.group_title)) {
                                AppGroupOption.normalObjects.forEach { option ->
                                    val newValue = option.invert(state.group)
                                    GkMenuItemCheckbox(
                                        enabled = newValue != 0,
                                        text = option.label,
                                        checked = option.include(state.group),
                                        onClick = { SettingsRepository.updateSettings { it.copy(appGroupType = newValue) } },
                                    )
                                }
                            }
                            GkMenuGroupCard(title = stringResource(Res.string.filter_title)) {
                                GkMenuItemCheckbox(
                                    text = stringResource(Res.string.whitelist_title),
                                    checked = state.showBlocked,
                                    onClick = {
                                        SettingsRepository.updateSettings { it.copy(showBlockApp = !state.showBlocked) }
                                    },
                                )
                            }
                        }
                    }
                    GkBlockCloseIconButton(
                        isClose = editWhiteListMode,
                        contentDescription = stringResource(Res.string.whitelist_edit_mode_toggle),
                        onClickLabel =
                            if (editWhiteListMode) stringResource(Res.string.edit_exit)
                            else stringResource(Res.string.edit_enter),
                        onClick = { controls.toggleEdit(blocked) },
                    )
                },
            )
        },
        floatingActionButton = {
            GkAnimatedFloatingActionButton(
                visible = editWhiteListMode,
                contentDescription = stringResource(Res.string.whitelist_edit),
                onClick = {
                    onNavigate(EditBlockAppListRoute)
                },
                imageVector = GkIcons.Edit,
            )
        },
    ) { contentPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = ::refresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                if (!state.canQueryPackages) {
                    item(key = 1, contentType = 1) {
                        GkQueryPkgAuthCard(state.refreshing, window::requestQueryPackages)
                    }
                }
                if (state.listState !is Loadable.Ready || state.ruleStatsFailed) {
                    item("load-state") {
                        Text(
                            stringResource(
                                if (state.listState is Loadable.Loading) Res.string.loading_progress
                                else Res.string.data_load_failed
                            ),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(appInfos, { it.id }) { appInfo ->
                    val stats = if (editWhiteListMode) null else state.ruleStats[appInfo.id]
                    GkAppListItem(
                        appInfo = appInfo,
                        icon = { window.GkAppIcon(appInfo.id, 32.dp) },
                        name = { GkAppNameText(appInfo = appInfo) },
                        stats = stats,
                        editWhiteListMode = editWhiteListMode,
                        inWhiteList = appInfo.id in state.whiteListAppIds,
                        onClick = {
                            if (editWhiteListMode) {
                                SettingsRepository.updateBlockMatchAppList {
                                    if (appInfo.id in it) it - appInfo.id else it + appInfo.id
                                }
                            } else {
                                window.hideIme()
                                onNavigate(AppConfigRoute(appInfo.id))
                            }
                        },
                    )
                }
                item("placeholder", "placeholder") {
                    if (
                        state.listState is Loadable.Ready &&
                            appInfos.isEmpty() &&
                            searchStr.isNotEmpty()
                    ) {
                        GkEmptyState(
                            text =
                                if (state.showAllApps) stringResource(Res.string.search_no_results)
                                else stringResource(Res.string.search_no_results_filter_hint)
                        )
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
fun GkAppListItem(
    appInfo: AppInfo,
    icon: @Composable () -> Unit,
    name: @Composable () -> Unit,
    stats: GkRuleStatsData?,
    editWhiteListMode: Boolean,
    inWhiteList: Boolean,
    onClick: () -> Unit,
) {
    val statsDescription = stats?.takeIf { it.hasRules }?.description
    val description =
        if (editWhiteListMode) appInfo.name
        else
            stringResource(
                Res.string.app_whitelist_state_description,
                appInfo.name,
                statsDescription ?: appInfo.id,
            )
    val member = stringResource(Res.string.whitelist_member)
    val notMember = stringResource(Res.string.whitelist_not_member)
    val clickLabel =
        if (editWhiteListMode)
            stringResource(
                if (inWhiteList) Res.string.whitelist_remove else Res.string.whitelist_add
            )
        else stringResource(Res.string.rule_summary_open)

    Row(
        modifier =
            Modifier.clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (inWhiteList) {
                        stateDescription = member
                    } else if (editWhiteListMode) {
                        stateDescription = notMember
                    }
                    onClick(
                        label = clickLabel,
                        action = null,
                    )
                }
                .itemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            name()
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
                modifier = Modifier.padding(2.dp).size(20.dp),
                imageVector = GkIcons.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
