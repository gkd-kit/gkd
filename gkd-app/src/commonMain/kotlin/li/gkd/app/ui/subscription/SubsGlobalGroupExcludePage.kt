package li.gkd.app.ui.subscription

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import li.gkd.app.ui.component.gkTopAppBarScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkTriggerCount
import androidx.compose.foundation.layout.padding
import li.gkd.app.model.ExcludeData
import li.gkd.app.ui.component.GkEditorDialogs
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.isFullVisible
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkAnimatedBooleanContent
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
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
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.home.rememberVisitOrder
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.image.GkAppIcon
import li.gkd.app.ui.navigation.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.app.AppSort
import li.gkd.app.model.findOption
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.platform.hideIme
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.SortUtils
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsGlobalGroupExcludePage(
    host: UiHost,
    route: SubsGlobalGroupExcludeRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { SubsGlobalGroupExcludeViewModel(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val environment = rememberRuleControlEnvironment()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val visits = rememberVisitOrder()
    val query by vm.query.collectAsStateWithLifecycle()
    val showSearchBar by vm.showSearchBar.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        vm.setShowSearchBar(false)
        vm.setQuery("")
        focusManager.clearFocus()
        host.hideIme()
    }

    val selection = vm.selection
    val selectionSnapshot by selection.state.collectAsStateWithLifecycle()
    val scroll = rememberListScrollState(canScroll = { draft == null })
    GkBackHandler(showSearchBar && !selectionSnapshot.active) {
        if (!host.hideIme()) closeSearch()
    }
    GkBackHandler(selectionSnapshot.active) {
        if (!busy) selection.clear()
    }
    GkSubscriptionPageContent(vm.uiState, mainVm.navigator::pop) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val group = state.group
        val editing = draft
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
            state.triggerStats,
            visits
        ) {
            val named = matchingIds.filter { it in filteredIds }
                .sortedWith { a, b ->
                    SortUtils.collator.compare(
                        environment.apps[a]?.name ?: a,
                        environment.apps[b]?.name ?: b
                    )
                }
            when (AppSort.options.findOption(settings.subsExcludeSort)) {
                AppSort.ByTriggerCount -> named.sortedByDescending { state.triggerStats.app(it).count }
                AppSort.ByAppName -> named
                AppSort.ByActionTime -> named.sortedBy {
                    state.appActionOrder[it] ?: Int.MAX_VALUE
                }

                AppSort.ByUsedTime -> named.sortedBy {
                    visits.value?.get(it) ?: Int.MAX_VALUE
                }
            }
        }
        scroll.ResetOnListChange(visibleIds, key = { it })
        val selectableTargets =
            visibleIds.filterTo(mutableSetOf()) { controls.getValue(it).canEnable }
        val selected = selectionSnapshot.selectedKeys intersect selectableTargets
        LaunchedEffect(selectableTargets) { selection.retain(selectableTargets) }
        fun applyToApps(ids: Set<String>, setting: RuleSetting, confirm: Boolean = false) {
            val request = vm.prepareSwitches(state, ids)
            vm.scope.launchUi {
                vm.runAction {
                    vm.applySwitches(
                        request, setting,
                        confirmation = if (confirm) getString(
                            Res.string.global_rule_app_switch_batch_confirmation,
                            ids.size.toString(),
                            setting.labelText()
                        ) else null,
                    )?.let { ToastUtils.show(it.description()) }
                }
            }
        }
        val editorDialog by vm.editorActions.dialog.collectAsStateWithLifecycle()
        val closeEditor: () -> Unit = {
            vm.editorActions.requestClose(
                hasChanges = { editing != null && ExcludeData.parse(editing.text) != editing.expected },
                beforeClose = { host.hideIme() },
                onClose = vm::closeEditor,
            )
        }
        val saveEditor: () -> Unit = {
            vm.editorActions.save(
                onSave = {
                    if (editing != null) {
                        val changed = vm.saveEditor(editing)
                        ToastUtils.show(getString(if (changed) Res.string.update_success else Res.string.unchanged))
                    }
                },
                beforeClose = { host.hideIme() },
                onSaved = vm::closeEditor,
            )
        }
        GkBackHandler(editing != null, onBack = closeEditor)
        GkScaffold(
            modifier = Modifier.gkTopAppBarScroll(scroll.scrollBehavior),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = selectionSnapshot.active,
                    selectedCount = selected.size,
                    onExitSelection = selection::clear,
                    onNavigateBack = {
                        when {
                            editing != null -> closeEditor()
                            showSearchBar -> closeSearch()
                            else -> mainVm.navigator.pop()
                        }
                    },
                    backOrClose = editing == null && !selectionSnapshot.active,
                    canScroll = editing == null,
                    onTitleClick = if (showSearchBar || editing != null) null else scroll::resetScroll,
                    scrollBehavior = scroll.scrollBehavior,
                    title = {
                        val firstShowSearchBar = remember { showSearchBar }
                        if (editing == null && showSearchBar) {
                            GkAppBarTextField(
                                value = query,
                                onValueChange = {
                                    // 关闭时失焦可能回传旧文本，不能恢复已清空的搜索条件。
                                    if (showSearchBar) vm.setQuery(it)
                                },
                                hint = stringResource(Res.string.app_search_hint),
                                modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                            )
                        } else {
                            GkTwoLineText(group.name, RuleProperty.Personal.label)
                        }
                    },
                    actions = { selectedMode ->
                        GkAnimatedBooleanContent(
                            targetState = editing != null,
                            contentAlignment = Alignment.TopEnd,
                            contentTrue = {
                                GkIconButton(
                                    imageVector = GkIcons.Check,
                                    contentDescription = stringResource(Res.string.action_save),
                                    onClick = saveEditor,
                                )
                            },
                            contentFalse = {
                                Row {
                                    if (selectedMode) {
                                        GkMultiSelectionActions(
                                            selectionSnapshot.selectedKeys,
                                            selectableTargets, selection::selectAll, selection::invert,
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
                                                if (!showSearchBar) vm.setShowSearchBar(true)
                                                else if (query.isNotEmpty()) vm.setQuery("")
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
                                    }
                                }
                            },
                        )
                    },
                )
            },
            floatingActionButton = {
                GkAnimatedFloatingActionButton(
                    visible = editing == null && !selectionSnapshot.active && !showSearchBar && scroll.scrollBehavior.isFullVisible,
                    imageVector = GkIcons.Edit,
                    contentDescription = stringResource(Res.string.action_edit),
                    onClick = { if (draft == null) vm.startEditing(state) },
                )
            },
        ) { padding ->
            if (editing != null) {
                GkMultiTextField(
                    modifier = Modifier.scaffoldPadding(padding),
                    text = editing.text,
                    onTextChange = vm::setDraftText,
                    immediateFocus = true,
                    placeholderText = stringResource(Res.string.global_rule_scope_input_hint),
                )
            } else {
                LazyColumn(Modifier.scaffoldPadding(padding), state = scroll.listState) {
                    items(visibleIds, key = { it }) { appId ->
                        val control = controls.getValue(appId)
                        GkRuleListItem(
                            onClick = { mainVm.showRuleGroup(subs.id, appId, group, appId) },
                            selectedMode = selectionSnapshot.active, selected = appId in selected,
                            selectable = control.canEnable,
                            selectionEnabled = !busy,
                            onSelect = { selection.toggle(appId) },
                            onLongClick = {
                                if (!busy && control.canEnable) {
                                    focusManager.clearFocus()
                                    host.hideIme()
                                    selection.select(appId)
                                }
                            },
                            leading = { GkAppIcon(appId, 32.dp) },
                            trailingInfo = {
                                val summary = state.triggerStats.app(appId)
                                GkTriggerCount(summary.count, Modifier.padding(horizontal = 8.dp), lastTriggerTime = summary.lastTime)
                            },
                            trailing = { switchModifier ->
                                GkRuleEnableControl(
                                    control, onSettingChange = { setting ->
                                        val request = vm.prepareSwitches(state, setOf(appId))
                                        vm.scope.launchUi {
                                            vm.applySwitches(request, setting)
                                                ?.failureMessage()
                                                ?.let { ToastUtils.show(it) }
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
                            val showAppId = appInfo != null && appInfo.name != appId
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
                                if (!showAppId) GkRulePropertyIndicators(control)
                            }
                            if (showAppId) GkRuleSupportingContent(control, appId)
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
        GkEditorDialogs(editorDialog, vm.editorActions::dismissDialog, vm::closeEditor)
    }
}
