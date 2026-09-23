package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.domain.rule.RuleConfigIndex
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.store.AppStore.blockMatchAppListFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkRuleListHeader
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.filterSubsApps
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.findOption

@Serializable
data class SubsCategoryGroupRoute(val subsId: Long, val categoryKey: Int) : NavKey

@Composable
fun SubsCategoryGroupPage(route: SubsCategoryGroupRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val environment = rememberRuleControlEnvironment()
    val vm = viewModel { SubsCategoryGroupVm(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val settings by storeFlow.collectAsStateWithLifecycle()
    val appMap by AppInfoRepository.appInfoMapFlow.collectAsStateWithLifecycle()
    val visitOrder by mainVm.appVisitOrderMapState.collectAsStateWithLifecycle()
    val blockApps by blockMatchAppListFlow.collectAsStateWithLifecycle()
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var retainForRemoval by remember { mutableStateOf(false) }
    GkSubscriptionPageContent(vm.uiState, retainContent = retainForRemoval) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val category = state.category
        val sortedApps = remember(state.apps, appMap, settings, state.appActionOrder, visitOrder, blockApps) {
            filterSubsApps(
                apps = state.apps,
                appMap = appMap,
                settings = settings,
                appActionOrderMap = state.appActionOrder,
                appVisitOrderMap = visitOrder.value.orEmpty(),
                blockSet = blockApps,
                appGroupType = { it.subsCategoryGroupType },
                sortType = { AppSortOption.objects.findOption(it.subsCategorySort) },
                showBlockApps = { it.subsCategoryShowBlock },
            )
        }
        val controls = remember(state, environment) { state.apps.flatMap { app -> app.groups.map { group ->
            (app.id to group.key) to environment.resolve(subs, group, app.id, state.configs, configIndex)
        } }.toMap() }
        val apps = sortedApps
        val visibleTargets = remember(apps, subs.id) {
            apps.flatMap { app -> app.groups.map { RuleGroupTarget.App(subs.id, app.id, it.key) } }.toSet()
        }
        val selectableTargets = remember(visibleTargets, controls) {
            visibleTargets.filterTo(mutableSetOf()) { controls.getValue(it.appId to it.groupKey).canEnable }
        }
        val selection = rememberMultiSelectionState<RuleGroupTarget.App>()
        val selected = selection.selectedKeys intersect selectableTargets
        LaunchedEffect(selectableTargets) { selection.retain(selectableTargets) }
        BackHandler(selection.active) { selection.clear() }
        val scroll = rememberListScrollState()
        fun updateGroups(targets: Set<RuleGroupTarget.App>, enabled: Boolean?, entireCategory: Boolean = false) {
            val request = vm.prepareSwitches(state, targets)
            vm.scope.launchUi {
                vm.runAction {
                    if (enabled == null) {
                        val scopeLabel = if (entireCategory) UiStrings.category_all_scope else UiStrings.selection_scope
                        val changedCount = request.expected.values.count { it != RuleSetting.FollowDefault }
                        val unchangedCount = request.expected.size - changedCount
                        if (!mainVm.dialogRequests.confirm(
                            title = UiStrings.rule_clear_custom_settings,
                            text = UiStrings.category_clear_settings_confirmation(scopeLabel, targets.size, if (entireCategory) UiStrings.category_scope_includes_hidden else UiStrings.category_scope_selected_only) +
                                "\n\n" + if (unchangedCount > 0)
                                    UiStrings.rule_switch_expected_counts(changedCount, unchangedCount)
                                else UiStrings.rule_switch_expected_changed_count(changedCount),
                        )) return@runAction
                    }
                    val result = vm.applySwitches(request, RuleSetting.from(enabled))
                    if (enabled == null) {
                        toast(result.failureMessage ?: if (result.restricted > 0)
                            UiStrings.rule_switch_restricted_count_suffix(result.restricted).trimStart('；')
                        else UiStrings.update_success)
                    } else {
                        toast(result.description)
                    }
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
                    onNavigateBack = mainVm::popPage,
                    scrollBehavior = scroll.scrollBehavior,
                    onTitleClick = scroll::resetScroll,
                    title = { GkTwoLineText(title = subs.name, subtitle = category.name) },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(selection, selectableTargets, enabled = !busy) { dismiss ->
                                GkBatchActionMenuItem(UiStrings.action_turn_on, dismiss, { updateGroups(selected, true) }, !busy)
                                GkBatchActionMenuItem(UiStrings.action_close, dismiss, { updateGroups(selected, false) }, !busy)
                                GkBatchActionMenuItem(
                                    UiStrings.settings_reset_default, dismiss,
                                    { updateGroups(selected intersect state.overrideTargets, null) },
                                    !busy && selected.any { it in state.overrideTargets },
                                )
                            }
                        } else {
                            Box {
                                GkFilterIconButton(filtered = visibleTargets.size < state.targets.size,
                                    contentDescription = UiStrings.app_sort_filter,
                                    onClick = { showFilter = true })
                                DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                                    GkAppFilterContent(
                                        sort = settings.subsCategorySort,
                                        groupType = settings.subsCategoryGroupType,
                                        showBlockApps = settings.subsCategoryShowBlock,
                                        onSort = vm::setSortType,
                                        onAppGroup = vm::setAppGroupType,
                                        onToggleBlock = vm::toggleShowBlockApps,
                                    )
                                }
                            }
                            GkIconButton(imageVector = GkIcons.MoreVert,
                                contentDescription = UiStrings.more_actions,
                                onClick = { showActions = true })
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(padding),
                state = scroll.listState,
            ) {
                apps.forEach { app ->
                    stickyHeader("app:${app.id}") {
                        GkRuleListHeader(
                            enabled = !selection.active,
                            onClickLabel = UiStrings.app_view_all_rules,
                            onClick = { mainVm.navigatePage(SubsAppGroupListRoute(subs.id, app.id)) },
                        ) {
                            GkAppNameText(
                                modifier = Modifier.weight(1f),
                                appId = app.id,
                                fallbackName = app.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(app.groups, key = { "group:${app.id}:${it.key}" }) { group ->
                        val target = RuleGroupTarget.App(subs.id, app.id, group.key)
                        GkRuleGroupCard(
                            subs = subs,
                            appId = app.id,
                            group = group,
                            control = controls.getValue(app.id to group.key),
                            hideCategoryPrefix = true,
                            onOpen = { mainVm.showRuleGroup(subs.id, app.id, group) },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(target))
                                vm.scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
                            },
                            isSelectedMode = selection.active,
                            isSelected = target in selected,
                            selectionEnabled = !busy,
                            onLongClick = { if (!busy) selection.select(target) },
                            onSelectedChange = { if (!busy) selection.toggle(target) },
                        )
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (apps.isEmpty()) {
                        GkEmptyState(
                            text = if (state.targets.isEmpty()) UiStrings.category_groups_empty else UiStrings.rule_groups_no_filter_matches,
                        )
                    }
                    GkPageBottomSpace()
                }
            }
        }
        if (showActions) {
            GkCategoryActionsSheet(
                category = category,
                setting = state.setting,
                overrideCount = state.overrideTargets.size,
                editable = subs.isLocal,
                busy = busy,
                onDismissRequest = { showActions = false },
                onSetting = { setting ->
                    vm.scope.launchUi { vm.setCategorySetting(subs, setting, state.setting) }
                },
                onClearOverrides = {
                    showActions = false
                    updateGroups(state.overrideTargets, null, entireCategory = true)
                },
                onEdit = { showActions = false; mainVm.navigatePage(CategoryEditorRoute(subs.id, category.key)) },
                onDelete = {
                    mainVm.confirmDelete(
                        title = UiStrings.category_delete,
                        text = UiStrings.category_delete_confirmation(category.name, state.targets.size),
                        targets = { setOf(DeletionTarget.Category(subs.id, category.key)) },
                        dismiss = {
                            showActions = false
                            retainForRemoval = true
                        },
                    ) {
                        vm.deleteCategory(subs)
                        toast(UiStrings.delete_success)
                    }
                },
            )
        }

    }
}
