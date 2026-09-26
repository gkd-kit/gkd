package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.core.state.Loadable
import li.gkd.app.domain.rule.RuleConfigIndex
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.filterSubsApps
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.findOption
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.db.LOCAL_SUBS_IDS
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkSubsAppCard
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.useSubs

@Serializable
data class SubsAppListRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsAppListPage(route: SubsAppListRoute) {
    val subsItemId = route.subsItemId

    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { SubsAppListVm(route) }
    val scope = vm.scope
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val subscription = useSubs(subsItemId)

    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val environment = rememberRuleControlEnvironment()
    val appInfoMap = environment.apps
    val store by storeFlow.collectAsStateWithLifecycle()
    val visits by mainVm.appVisitOrderMapState.collectAsStateWithLifecycle()
    var searchStr by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        showSearchBar = false
        searchStr = ""
        focusManager.clearFocus()
        context.imeController.requestHide()
    }
    val state = loadableState.value
    val configIndex = remember(state?.configs) { state?.configs?.let(::RuleConfigIndex) }
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val sortedApps = remember(state, environment, store, visits) {
        filterSubsApps(state?.subscription?.apps.orEmpty(), environment.apps, store,
            state?.appActionOrder.orEmpty(), visits.value.orEmpty(), environment.blockedApps,
            { it.subsAppGroupType }, { AppSortOption.objects.findOption(it.subsAppSort) }, { it.subsAppShowBlock })
    }
    val apps = remember(sortedApps, searchStr, appInfoMap) { sortedApps.filter { app ->
        app.id.contains(searchStr, true) || (appInfoMap[app.id]?.name ?: app.name).orEmpty().contains(searchStr, true)
    } }
    val selection = rememberMultiSelectionState<String>()
    val visibleTargets = apps.mapTo(mutableSetOf()) { it.id }
    val selected = selection.selectedKeys intersect visibleTargets
    LaunchedEffect(visibleTargets) { selection.retain(visibleTargets) }
    BackHandler(showSearchBar && !selection.active) {
        if (!context.imeController.requestHide()) closeSearch()
    }
    BackHandler(selection.active) { if (!busy) selection.clear() }
    val updateSelected: (RuleSetting) -> Unit = { setting ->
        val request = vm.prepareSwitches(checkNotNull(state), selected)
        scope.launchUi {
            vm.runAction {
                if (mainVm.dialogRequests.confirm(UiStrings.subscription_app_switch_set, UiStrings.subscription_app_switch_batch_confirmation(selected.size, setting.label))) {
                    toast(vm.applySwitches(request, setting).description)
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = selection.active, selectedCount = selected.size,
                onExitSelection = selection::clear,
                onNavigateBack = { if (showSearchBar) closeSearch() else mainVm.popPage() },
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
                            hint = UiStrings.app_search_hint,
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        GkTwoLineText(title = subscription?.name ?: subsItemId.toString(), subtitle = UiStrings.app_rules)
                    }
            }, actions = { selectedMode ->
                if (selectedMode) {
                    GkMultiSelectionActions(selection, visibleTargets, !busy) { dismiss ->
                        GkRuleBatchMenuItems(!busy, dismiss, updateSelected)
                    }
                } else {
                    GkSearchCloseIconButton(
                        isSearchOpen = showSearchBar,
                        contentDescription = if (!showSearchBar) UiStrings.search_open else if (searchStr.isEmpty()) UiStrings.search_close else UiStrings.search_clear,
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
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                FloatingActionButton(onClick = throttle {
                    mainVm.navigatePage(
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
                        environment.resolve(current.subscription, group, app.id, current.configs,
                            checkNotNull(configIndex)).configuredEnabled
                    }
                }
                GkSubsAppCard(
                    subsId = subsItemId,
                    rawApp = app,
                    enabledGroupCount = enabledGroupCount,
                    appInfo = appInfoMap[app.id],
                    control = environment.app(subsItemId, app.id, checkNotNull(state).configs,
                        checkNotNull(configIndex)),
                    selectionEnabled = !busy,
                    selectedMode = selection.active,
                    selected = app.id in selected,
                    onLongClick = {
                        if (!busy) {
                            focusManager.clearFocus()
                            context.imeController.requestHide()
                            selection.select(app.id)
                        }
                    },
                    onSelect = { selection.toggle(app.id) },
                    onClick = throttle {
                        context.imeController.requestHide()
                        mainVm.navigatePage(SubsAppGroupListRoute(subsItemId, app.id))
                    },
                    onSettingChange = { setting ->
                        val request = vm.prepareSwitches(checkNotNull(state), setOf(app.id))
                        scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                if (apps.isEmpty() && !firstLoading) {
                    GkEmptyState(
                        text = if (loadError != null) {
                            loadError.message ?: UiStrings.subscription_load_failed
                        } else if (searchStr.isNotEmpty()) {
                            if (showAllApps) UiStrings.search_no_results else UiStrings.search_no_results_filter_hint
                        } else {
                            UiStrings.rules_empty
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
