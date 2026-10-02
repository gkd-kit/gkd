package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_rules
import li.gkd.app.resources.app_search_hint
import li.gkd.app.resources.rules_empty
import li.gkd.app.resources.search_clear
import li.gkd.app.resources.search_close
import li.gkd.app.resources.search_no_results
import li.gkd.app.resources.search_no_results_filter_hint
import li.gkd.app.resources.search_open
import li.gkd.app.resources.subscription_app_switch_batch_confirmation
import li.gkd.app.resources.subscription_app_switch_set
import li.gkd.app.resources.subscription_load_failed
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleSetting
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSubsAppCard
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.home.rememberVisitOrder
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.text.subscriptionMessageResource
import li.gkd.db.LOCAL_SUBS_IDS
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsAppListPage(
    route: SubsAppListRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
    hideIme: () -> Boolean,
    appIcon: @Composable (String, Dp) -> Unit,
) {
    val subsItemId = route.subsItemId

    val vm = viewModel { SubsAppListViewModel(route) }
    val scope = vm.scope
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val subscriptionSnapshot by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
    val subscription = subscriptionSnapshot.value?.subscriptions?.get(subsItemId)

    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val environment = rememberRuleControlEnvironment()
    val appInfoMap = environment.apps
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val visits = rememberVisitOrder()
    var searchStr by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        showSearchBar = false
        searchStr = ""
        focusManager.clearFocus()
        hideIme()
    }

    val state = loadableState.value
    val configIndex = remember(state?.configs) { state?.configs?.let(::RuleConfigIndex) }
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val sortedApps = remember(state, environment, store, visits) {
        filterSubsApps(
            state?.subscription?.apps.orEmpty(),
            environment.apps,
            store,
            state?.appActionOrder.orEmpty(),
            visits.value.orEmpty(),
            environment.blockedApps,
            { it.subsAppGroupType },
            { AppSortOption.objects.findOption(it.subsAppSort) },
            { it.subsAppShowBlock },
        )
    }
    val apps = remember(sortedApps, searchStr, appInfoMap) {
        sortedApps.filter { app ->
            app.id.contains(searchStr, true) || (appInfoMap[app.id]?.name ?: app.name).orEmpty()
                .contains(searchStr, true)
        }
    }
    val selection = rememberMultiSelectionState<String>()
    val visibleTargets = apps.mapTo(mutableSetOf()) { it.id }
    val selected = selection.selectedKeys intersect visibleTargets
    LaunchedEffect(visibleTargets) { selection.retain(visibleTargets) }
    GkBackHandler(showSearchBar && !selection.active) {
        if (!hideIme()) closeSearch()
    }
    GkBackHandler(selection.active) { if (!busy) selection.clear() }
    val updateSelected: (RuleSetting) -> Unit = { setting ->
        val request = vm.prepareSwitches(checkNotNull(state), selected)
        launchUi(scope, showToast) {
            vm.runAction {
                if (dialogs.confirm(
                        getString(Res.string.subscription_app_switch_set),
                        getString(
                            Res.string.subscription_app_switch_batch_confirmation,
                            selected.size.toString(),
                            setting.labelText()
                        )
                    )
                ) {
                    showToast(vm.applySwitches(request, setting).description())
                }
            }
        }
    }
    val showAllApps = apps.size == state?.subscription?.apps?.size
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnListChange(apps, key = { it.id })
    var expanded by remember { mutableStateOf(false) }

    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = selection.active, selectedCount = selected.size,
                onExitSelection = selection::clear,
                onNavigateBack = { if (showSearchBar) closeSearch() else onBack() },
                onTitleClick = if (showSearchBar) null else pageScrollState::resetScroll,
                scrollBehavior = scrollBehavior, title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        GkAppBarTextField(
                            value = searchStr,
                            onValueChange = {
                                // 关闭时失焦可能回传旧文本，不能恢复已清空的搜索条件。
                                if (showSearchBar) searchStr = it
                            },
                            hint = stringResource(Res.string.app_search_hint),
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        GkTwoLineText(
                            title = subscription?.name ?: subsItemId.toString(),
                            subtitle = stringResource(Res.string.app_rules)
                        )
                    }
                }, actions = { selectedMode ->
                    if (selectedMode) {
                        GkMultiSelectionActions(selection, visibleTargets, !busy) { dismiss ->
                            GkRuleBatchMenuItems(!busy, dismiss, updateSelected)
                        }
                    } else {
                        GkSearchCloseIconButton(
                            isSearchOpen = showSearchBar,
                            contentDescription = if (!showSearchBar) stringResource(Res.string.search_open) else if (searchStr.isEmpty()) stringResource(
                                Res.string.search_close
                            ) else stringResource(Res.string.search_clear),
                            onClick = {
                                if (!showSearchBar) showSearchBar = true
                                else if (searchStr.isNotEmpty()) searchStr = ""
                                else closeSearch()
                            },
                        )
                        GkFilterIconButton(
                            filtered = sortedApps.size < state?.subscription?.apps.orEmpty().size,
                            onClick = {
                                expanded = true
                            },
                        )
                        Box(
                            modifier = Modifier.wrapContentSize(Alignment.TopStart)
                        ) {
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                GkAppFilterContent(
                                    sort = store.subsAppSort,
                                    groupType = store.subsAppGroupType,
                                    showBlockApps = store.subsAppShowBlock,
                                    onSort = vm::setSortType,
                                    onAppGroup = vm::setAppGroupType,
                                    onToggleBlock = vm::toggleShowBlockApps,
                                )
                            }
                        }
                    }
                })
        },
        floatingActionButton = {
            if (LOCAL_SUBS_IDS.contains(subsItemId) && !selection.active) {
                FloatingActionButton(onClick = {
                    onNavigate(
                        UpsertRuleGroupRoute(
                            subsId = subsItemId,
                            groupKey = null,
                            appId = "",
                            forward = true,
                        )
                    )
                }) {
                    GkIcon(
                        imageVector = GkIcons.Add,
                    )
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            items(apps, { it.id }) { app ->
                val enabledGroupCount = remember(app, state, environment) {
                    val current = checkNotNull(state)
                    app.groups.count { group ->
                        // Match the child list's switch positions, independent of parent availability.
                        environment.resolve(
                            current.subscription, group, app.id, current.configs,
                            checkNotNull(configIndex)
                        ).configuredEnabled
                    }
                }
                GkSubsAppCard(
                    appIcon = { appIcon(app.id, 32.dp) },
                    appName = { GkAppNameText(app.id, app.name) },
                    subsId = subsItemId,
                    rawApp = app,
                    enabledGroupCount = enabledGroupCount,
                    appInfo = appInfoMap[app.id],
                    control = environment.app(
                        subsItemId, app.id, checkNotNull(state).configs,
                        checkNotNull(configIndex)
                    ),
                    selectionEnabled = !busy,
                    selectedMode = selection.active,
                    selected = app.id in selected,
                    onLongClick = {
                        if (!busy) {
                            focusManager.clearFocus()
                            hideIme()
                            selection.select(app.id)
                        }
                    },
                    onSelect = { selection.toggle(app.id) },
                    onClick = {
                        hideIme()
                        onNavigate(SubsAppGroupListRoute(subsItemId, app.id))
                    },
                    onSettingChange = { setting ->
                        val request = vm.prepareSwitches(checkNotNull(state), setOf(app.id))
                        launchUi(scope, showToast) {
                            vm.applySwitches(request, setting).failureMessage()
                                ?.let { showToast(it) }
                        }
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                if (apps.isEmpty() && !firstLoading) {
                    GkEmptyState(
                        text = if (loadError != null) {
                            loadError.subscriptionMessageResource() ?: stringResource(Res.string.subscription_load_failed)
                        } else if (searchStr.isNotEmpty()) {
                            if (showAllApps) stringResource(Res.string.search_no_results) else stringResource(
                                Res.string.search_no_results_filter_hint
                            )
                        } else {
                            stringResource(Res.string.rules_empty)
                        }
                    )
                    GkPageBottomSpace(height = GkPageBottomSpaceDefaults.CompactHeight)
                } else {
                    GkPageBottomSpace()
                }
            }
        }
    }
}
