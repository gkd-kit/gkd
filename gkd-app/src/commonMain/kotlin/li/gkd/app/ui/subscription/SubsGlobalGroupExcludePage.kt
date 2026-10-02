package li.gkd.app.ui.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_edit
import li.gkd.app.resources.app_search_hint
import li.gkd.app.resources.apps_no_matches
import li.gkd.app.resources.apps_no_matches_filter_hint
import li.gkd.app.resources.global_rule_app_switch_batch_confirmation
import li.gkd.app.resources.global_rule_app_switch_set
import li.gkd.app.resources.search_clear
import li.gkd.app.resources.search_close
import li.gkd.app.resources.search_open
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleEnableControl
import li.gkd.app.ui.component.GkRuleListItem
import li.gkd.app.ui.component.GkRulePropertyIndicators
import li.gkd.app.ui.component.GkRuleSupportingContent
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.RuleProperty
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.home.rememberVisitOrder
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.RuleExcludeEditorRoute
import li.gkd.app.ui.navigation.ShowRuleGroup
import li.gkd.app.ui.navigation.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.SortUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsGlobalGroupExcludePage(
    route: SubsGlobalGroupExcludeRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
    showRuleGroup: ShowRuleGroup,
    hideIme: () -> Boolean,
    appIcon: @Composable (String, Dp) -> Unit,
) {
    val vm = viewModel { SubsGlobalGroupExcludeViewModel(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val environment = rememberRuleControlEnvironment()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val visits = rememberVisitOrder()
    var query by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        showSearchBar = false
        query = ""
        focusManager.clearFocus()
        hideIme()
    }

    val selection = rememberMultiSelectionState<String>()
    val scroll = rememberListScrollState()
    GkBackHandler(showSearchBar && !selection.active) {
        if (!hideIme()) closeSearch()
    }
    GkBackHandler(selection.active) {
        if (!busy) selection.clear()
    }
    GkSubscriptionPageContent(vm.uiState, onBack) { state ->
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
                id.contains(query, true) || environment.apps[id]?.name?.contains(
                    query,
                    true
                ) == true
            }
        }
        val filteredIds = remember(controls, environment, settings) {
            controls.keys.filterTo(mutableSetOf()) { id ->
                (settings.subsExcludeShowBlockApp || id !in environment.blockedApps) &&
                        (environment.apps[id]?.let { info ->
                            (if (info.isSystem) AppGroupOption.SystemGroup else AppGroupOption.UserGroup).include(
                                settings.subsExcludeAppGroupType
                            )
                        } ?: true)
            }
        }
        val visibleIds = remember(
            matchingIds,
            filteredIds,
            environment,
            settings,
            state.appActionOrder,
            visits
        ) {
            val named = matchingIds.filter { it in filteredIds }
                .sortedWith { a, b ->
                    SortUtils.collator.compare(
                        environment.apps[a]?.name ?: a,
                        environment.apps[b]?.name ?: b
                    )
                }
            when (AppSortOption.objects.findOption(settings.subsExcludeSort)) {
                AppSortOption.ByAppName -> named
                AppSortOption.ByActionTime -> named.sortedBy {
                    state.appActionOrder[it] ?: Int.MAX_VALUE
                }

                AppSortOption.ByUsedTime -> named.sortedBy {
                    visits.value?.get(it) ?: Int.MAX_VALUE
                }
            }
        }
        scroll.ResetOnListChange(visibleIds, key = { it })
        val selectableTargets =
            visibleIds.filterTo(mutableSetOf()) { controls.getValue(it).canEnable }
        val selected = selection.selectedKeys intersect selectableTargets
        LaunchedEffect(selectableTargets) { selection.retain(selectableTargets) }
        fun applyToApps(ids: Set<String>, setting: RuleSetting, confirm: Boolean = false) {
            val request = vm.prepareSwitches(state, ids)
            launchUi(vm.scope, showToast) {
                vm.runAction {
                    if (confirm && !dialogs.confirm(
                            getString(Res.string.global_rule_app_switch_set),
                            getString(
                                Res.string.global_rule_app_switch_batch_confirmation,
                                ids.size.toString(),
                                setting.labelText()
                            )
                        )
                    ) return@runAction
                    showToast(vm.applySwitches(request, setting).description())
                }
            }
        }
        GkScaffold(
            modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = selection.active,
                    selectedCount = selected.size,
                    onExitSelection = selection::clear,
                    onNavigateBack = {
                        when {
                            showSearchBar -> closeSearch()
                            else -> onBack()
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
                                hint = stringResource(Res.string.app_search_hint),
                                modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                            )
                        } else {
                            GkTwoLineText(group.name, RuleProperty.Personal.label)
                        }
                    },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(
                                selection,
                                selectableTargets,
                                !busy
                            ) { dismiss ->
                                GkRuleBatchMenuItems(
                                    !busy,
                                    dismiss,
                                    { applyToApps(selected, it, true) })
                            }
                        } else {
                            GkSearchCloseIconButton(
                                isSearchOpen = showSearchBar,
                                contentDescription = if (!showSearchBar) stringResource(Res.string.search_open) else if (query.isEmpty()) stringResource(
                                    Res.string.search_close
                                ) else stringResource(Res.string.search_clear),
                                onClick = {
                                    if (!showSearchBar) showSearchBar = true
                                    else if (query.isNotEmpty()) query = ""
                                    else closeSearch()
                                },
                            )
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                GkFilterIconButton(
                                    filtered = filteredIds.size < controls.size,
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
                                        shrinkHorizontally(
                                            tween(300),
                                            shrinkTowards = Alignment.End
                                        ),
                            ) {
                                GkIconButton(
                                    imageVector = GkIcons.Edit,
                                    contentDescription = stringResource(Res.string.action_edit),
                                    enabled = !busy,
                                    onClick = {
                                        onNavigate(
                                            RuleExcludeEditorRoute(
                                                subs.id,
                                                group.key
                                            )
                                        )
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
                        onClick = { showRuleGroup(subs.id, appId, group, appId) },
                        selectedMode = selection.active, selected = appId in selected,
                        selectable = control.canEnable,
                        selectionEnabled = !busy,
                        onSelect = { selection.toggle(appId) },
                        onLongClick = {
                            if (!busy && control.canEnable) {
                                focusManager.clearFocus()
                                hideIme()
                                selection.select(appId)
                            }
                        },
                        leading = { appIcon(appId, 32.dp) },
                        trailing = { switchModifier ->
                            GkRuleEnableControl(
                                control, onSettingChange = { setting ->
                                    val request = vm.prepareSwitches(state, setOf(appId))
                                    launchUi(vm.scope, showToast) {
                                        vm.applySwitches(request, setting)
                                            .failureMessage()
                                            ?.let { showToast(it) }
                                    }
                                }, modifier = switchModifier,
                                identity = RuleSwitchTarget.GlobalApp(
                                    subs.id,
                                    group.key,
                                    appId
                                )
                            )
                        },
                    ) {
                        val appInfo = environment.apps[appId]
                        Row(
                            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                appInfo?.name ?: appId,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (appInfo == null) GkRulePropertyIndicators(control)
                        }
                        if (appInfo != null) GkRuleSupportingContent(control, appId)
                    }
                }
                item("empty") {
                    if (visibleIds.isEmpty()) {
                        GkEmptyState(
                            if (matchingIds.isEmpty()) stringResource(Res.string.apps_no_matches)
                            else stringResource(Res.string.apps_no_matches_filter_hint)
                        )
                    }
                    GkPageBottomSpace()
                }
            }
        }
    }
}
