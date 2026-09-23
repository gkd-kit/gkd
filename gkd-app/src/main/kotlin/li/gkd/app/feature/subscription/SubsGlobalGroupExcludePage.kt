package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.domain.rule.RuleConfigIndex
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.collator
import li.gkd.app.util.findOption
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppIcon
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleEnableControl
import li.gkd.app.ui.component.GkRuleListItem
import li.gkd.app.ui.component.GkRulePropertyIndicators
import li.gkd.app.ui.component.GkRuleSupportingContent
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.RuleProperty
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment

@Serializable
data class SubsGlobalGroupExcludeRoute(val subsItemId: Long, val groupKey: Int) : NavKey

@Composable
fun SubsGlobalGroupExcludePage(route: SubsGlobalGroupExcludeRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { SubsGlobalGroupExcludeVm(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val environment = rememberRuleControlEnvironment()
    val settings by storeFlow.collectAsStateWithLifecycle()
    val visits by mainVm.appVisitOrderMapState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    val context = LocalActivity.current as MainActivity
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        showSearchBar = false
        query = ""
        focusManager.clearFocus()
        context.imeController.requestHide()
    }
    val selection = rememberMultiSelectionState<String>()
    val scroll = rememberListScrollState()
    BackHandler(showSearchBar && !selection.active) {
        if (!context.imeController.requestHide()) closeSearch()
    }
    BackHandler(selection.active) {
        if (!busy) selection.clear()
    }
    GkSubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val group = state.group
        val controls = remember(state, environment) {
            (environment.apps.keys + state.declaredAppIds).associateWith {
                environment.resolve(subs, group, it, state.configs, configIndex)
            }
        }
        val matchingIds = remember(controls, environment, query) {
            controls.keys.filter { id ->
                id.contains(query, true) || environment.apps[id]?.name?.contains(query, true) == true
            }
        }
        val filteredIds = remember(controls, environment, settings) {
            controls.keys.filterTo(mutableSetOf()) { id ->
                (settings.subsExcludeShowBlockApp || id !in environment.blockedApps) &&
                    (environment.apps[id]?.let { info ->
                        (if (info.isSystem) AppGroupOption.SystemGroup else AppGroupOption.UserGroup).include(settings.subsExcludeAppGroupType)
                    } ?: true)
            }
        }
        val visibleIds = remember(matchingIds, filteredIds, environment, settings, state.appActionOrder, visits) {
            val named = matchingIds.filter { it in filteredIds }
                .sortedWith { a, b -> collator.compare(environment.apps[a]?.name ?: a, environment.apps[b]?.name ?: b) }
            when (AppSortOption.objects.findOption(settings.subsExcludeSort)) {
                AppSortOption.ByAppName -> named
                AppSortOption.ByActionTime -> named.sortedBy { state.appActionOrder[it] ?: Int.MAX_VALUE }
                AppSortOption.ByUsedTime -> named.sortedBy { visits.value?.get(it) ?: Int.MAX_VALUE }
            }
        }
        val selectableTargets = visibleIds.filterTo(mutableSetOf()) { controls.getValue(it).canEnable }
        val selected = selection.selectedKeys intersect selectableTargets
        LaunchedEffect(selectableTargets) { selection.retain(selectableTargets) }
        fun applyToApps(ids: Set<String>, setting: RuleSetting, confirm: Boolean = false) {
            val request = vm.prepareSwitches(state, ids)
            vm.scope.launchUi {
                vm.runAction {
                    if (confirm && !mainVm.dialogRequests.confirm(UiStrings.global_rule_app_switch_set,
                            UiStrings.global_rule_app_switch_batch_confirmation(ids.size, setting.label))) return@runAction
                    toast(vm.applySwitches(request, setting).description)
                }
            }
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = selection.active,
                    selectedCount = selected.size,
                    onExitSelection = selection::clear,
                    onNavigateBack = {
                        when {
                            showSearchBar -> closeSearch()
                            else -> mainVm.popPage()
                        }
                    },
                    onTitleClick = if (showSearchBar) null else scroll::resetScroll,
                    scrollBehavior = scroll.scrollBehavior,
                    title = {
                        val firstShowSearchBar = remember { showSearchBar }
                        if (showSearchBar) {
                            GkAppBarTextField(
                                value = query,
                                onValueChange = {
                                    // 关闭时失焦可能回传旧文本，不能恢复已清空的搜索条件。
                                    if (showSearchBar) query = it
                                },
                                hint = UiStrings.app_search_hint,
                                modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                            )
                        } else {
                            GkTwoLineText(group.name, RuleProperty.Personal.label)
                        }
                    },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(selection, selectableTargets, !busy) { dismiss ->
                                GkRuleBatchMenuItems(!busy, dismiss, { applyToApps(selected, it, true) })
                            }
                        } else {
                            GkSearchCloseIconButton(
                                isSearchOpen = showSearchBar,
                                contentDescription = if (!showSearchBar) UiStrings.search_open else if (query.isEmpty()) UiStrings.search_close else UiStrings.search_clear,
                                onClick = {
                                    if (!showSearchBar) showSearchBar = true
                                    else if (query.isNotEmpty()) query = ""
                                    else closeSearch()
                                },
                            )
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                GkFilterIconButton(filtered = filteredIds.size < controls.size,
                                    enabled = !busy, onClick = { menu = true })
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    GkAppFilterContent(
                                        sort = settings.subsExcludeSort,
                                        groupType = settings.subsExcludeAppGroupType,
                                        showBlockApps = settings.subsExcludeShowBlockApp,
                                        onSort = vm::setSortType,
                                        onAppGroup = vm::setAppGroupType,
                                        onToggleBlock = vm::toggleShowBlockApps,
                                        includeUninstalled = false,
                                        allowEmptyGroups = true,
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = !showSearchBar,
                                enter = slideInHorizontally(tween(300)) { it } +
                                    expandHorizontally(tween(300), expandFrom = Alignment.End),
                                exit = slideOutHorizontally(tween(300)) { it } +
                                    shrinkHorizontally(tween(300), shrinkTowards = Alignment.End),
                            ) {
                                GkIconButton(imageVector = GkIcons.Edit, contentDescription = UiStrings.action_edit,
                                    enabled = !busy, onClick = {
                                    mainVm.navigatePage(RuleExcludeEditorRoute(subs.id, group.key))
                                })
                            }
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.scaffoldPadding(padding), state = scroll.listState) {
                items(visibleIds, key = { it }) { appId ->
                    val control = controls.getValue(appId)
                    GkRuleListItem(
                        onClick = { mainVm.showRuleGroup(subs.id, appId, group) },
                        selectedMode = selection.active, selected = appId in selected,
                        selectable = control.canEnable,
                        selectionEnabled = !busy,
                        onSelect = { selection.toggle(appId) },
                        onLongClick = {
                            if (!busy && control.canEnable) {
                                focusManager.clearFocus()
                                context.imeController.requestHide()
                                selection.select(appId)
                            }
                        },
                        leading = { GkAppIcon(appId = appId) },
                        trailing = { switchModifier ->
                            GkRuleEnableControl(control, onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(appId))
                                vm.scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
                            }, modifier = switchModifier,
                                identity = li.gkd.app.domain.rule.RuleSwitchTarget.GlobalApp(subs.id, group.key, appId))
                        },
                    ) {
                        val appInfo = environment.apps[appId]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(appInfo?.name ?: appId, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (appInfo == null) GkRulePropertyIndicators(control)
                        }
                        if (appInfo != null) GkRuleSupportingContent(control, appId)
                    }
                }
                item("empty") {
                    if (visibleIds.isEmpty()) {
                        GkEmptyState(if (matchingIds.isEmpty()) UiStrings.apps_no_matches
                            else UiStrings.apps_no_matches_filter_hint)
                    }
                    GkPageBottomSpace()
                }
            }
        }
    }
}
