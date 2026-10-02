package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_turn_on
import li.gkd.app.resources.app_sort_filter
import li.gkd.app.resources.app_view_all_rules
import li.gkd.app.resources.category_all_scope
import li.gkd.app.resources.category_clear_settings_confirmation
import li.gkd.app.resources.category_delete
import li.gkd.app.resources.category_delete_confirmation
import li.gkd.app.resources.category_groups_empty
import li.gkd.app.resources.category_scope_includes_hidden
import li.gkd.app.resources.category_scope_selected_only
import li.gkd.app.resources.delete_success
import li.gkd.app.resources.more_actions
import li.gkd.app.resources.rule_clear_custom_settings
import li.gkd.app.resources.rule_groups_no_filter_matches
import li.gkd.app.resources.rule_switch_expected_changed_count
import li.gkd.app.resources.rule_switch_expected_counts
import li.gkd.app.resources.rule_switch_restricted_count_suffix
import li.gkd.app.resources.selection_scope
import li.gkd.app.resources.settings_reset_default
import li.gkd.app.resources.update_success
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAppFilterContent
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkRuleListHeader
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.home.rememberVisitOrder
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.navigation.ConfirmDeletion
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.ShowRuleGroup
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsCategoryGroupRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.text.getSync
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsCategoryGroupPage(
    route: SubsCategoryGroupRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
    confirmDelete: ConfirmDeletion,
    showRuleGroup: ShowRuleGroup,
) {
    val environment = rememberRuleControlEnvironment()
    val vm = viewModel { SubsCategoryGroupViewModel(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val appMap = rememberRuleControlEnvironment().apps
    val visitOrder = rememberVisitOrder()
    val blockApps by SettingsRepository.blockMatchAppList.collectAsStateWithLifecycle()
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var retainForRemoval by remember { mutableStateOf(false) }
    GkSubscriptionPageContent(
        vm.uiState,
        onBack = onBack,
        retainContent = retainForRemoval
    ) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val category = state.category
        val sortedApps =
            remember(state.apps, appMap, settings, state.appActionOrder, visitOrder, blockApps) {
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
        val controls = remember(state, environment) {
            state.apps.flatMap { app ->
                app.groups.map { group ->
                    (app.id to group.key) to environment.resolve(
                        subs,
                        group,
                        app.id,
                        state.configs,
                        configIndex
                    )
                }
            }.toMap()
        }
        val apps = sortedApps
        val visibleTargets = remember(apps, subs.id) {
            apps.flatMap { app -> app.groups.map { RuleGroupTarget.App(subs.id, app.id, it.key) } }
                .toSet()
        }
        val selectableTargets = remember(visibleTargets, controls) {
            visibleTargets.filterTo(mutableSetOf()) { controls.getValue(it.appId to it.groupKey).canEnable }
        }
        val selection = rememberMultiSelectionState<RuleGroupTarget.App>()
        val selected = selection.selectedKeys intersect selectableTargets
        LaunchedEffect(selectableTargets) { selection.retain(selectableTargets) }
        GkBackHandler(selection.active) { selection.clear() }
        val scroll = rememberListScrollState()
        fun updateGroups(
            targets: Set<RuleGroupTarget.App>,
            enabled: Boolean?,
            entireCategory: Boolean = false
        ) {
            val request = vm.prepareSwitches(state, targets)
            launchUi(vm.scope, showToast) {
                vm.runAction {
                    if (enabled == null) {
                        val scopeLabel =
                            if (entireCategory) getString(Res.string.category_all_scope) else getString(
                                Res.string.selection_scope
                            )
                        val changedCount =
                            request.expected.values.count { it != RuleSetting.FollowDefault }
                        val unchangedCount = request.expected.size - changedCount
                        if (!dialogs.confirm(
                                title = getString(Res.string.rule_clear_custom_settings),
                                text = getString(
                                    Res.string.category_clear_settings_confirmation,
                                    scopeLabel,
                                    targets.size.toString(),
                                    if (entireCategory) getString(Res.string.category_scope_includes_hidden) else getString(
                                        Res.string.category_scope_selected_only
                                    )
                                ) +
                                        "\n\n" + if (unchangedCount > 0)
                                    getString(
                                        Res.string.rule_switch_expected_counts,
                                        changedCount.toString(),
                                        unchangedCount.toString()
                                    )
                                else getString(
                                    Res.string.rule_switch_expected_changed_count,
                                    changedCount.toString()
                                ),
                            )
                        ) return@runAction
                    }
                    val result = vm.applySwitches(request, RuleSetting.from(enabled))
                    if (enabled == null) {
                        showToast(
                            result.failureMessage()
                                ?: if (result.restricted > 0)
                                    getString(
                                        Res.string.rule_switch_restricted_count_suffix,
                                        result.restricted.toString()
                                    ).trimStart('；')
                                else getString(Res.string.update_success)
                        )
                    } else {
                        showToast(result.description())
                    }
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
                    onNavigateBack = onBack,
                    scrollBehavior = scroll.scrollBehavior,
                    onTitleClick = scroll::resetScroll,
                    title = { GkTwoLineText(title = subs.name, subtitle = category.name) },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(
                                selection,
                                selectableTargets,
                                enabled = !busy
                            ) { dismiss ->
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.action_turn_on),
                                    dismiss,
                                    { updateGroups(selected, true) },
                                    !busy
                                )
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.action_close),
                                    dismiss,
                                    { updateGroups(selected, false) },
                                    !busy
                                )
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.settings_reset_default), dismiss,
                                    {
                                        updateGroups(
                                            selected intersect state.overrideTargets,
                                            null
                                        )
                                    },
                                    !busy && selected.any { it in state.overrideTargets },
                                )
                            }
                        } else {
                            Box {
                                GkFilterIconButton(
                                    filtered = visibleTargets.size < state.targets.size,
                                    contentDescription = stringResource(Res.string.app_sort_filter),
                                    onClick = { showFilter = true })
                                DropdownMenu(
                                    expanded = showFilter,
                                    onDismissRequest = { showFilter = false }) {
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
                            GkIconButton(
                                imageVector = GkIcons.MoreVert,
                                contentDescription = stringResource(Res.string.more_actions),
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
                            onClickLabel = stringResource(Res.string.app_view_all_rules),
                            onClick = {
                                onNavigate(
                                    SubsAppGroupListRoute(
                                        subs.id,
                                        app.id
                                    )
                                )
                            },
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
                            onOpen = { showRuleGroup(subs.id, app.id, group, app.id) },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(target))
                                launchUi(vm.scope, showToast) {
                                    vm.applySwitches(request, setting)
                                        .failureMessage()?.let { showToast(it) }
                                }
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
                            text = if (state.targets.isEmpty()) stringResource(Res.string.category_groups_empty) else stringResource(
                                Res.string.rule_groups_no_filter_matches
                            ),
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
                    launchUi(vm.scope, showToast) {
                        vm.setCategorySetting(
                            subs,
                            setting,
                            state.setting
                        )
                    }
                },
                onClearOverrides = {
                    showActions = false
                    updateGroups(state.overrideTargets, null, entireCategory = true)
                },
                onEdit = {
                    showActions = false; onNavigate(
                    CategoryEditorRoute(
                        subs.id,
                        category.key
                    )
                )
                },
                onDelete = {
                    confirmDelete(
                        Res.string.category_delete.getSync(),
                        Res.string.category_delete_confirmation.getSync(
                            category.name,
                            state.targets.size.toString()
                        ),
                        { setOf(DeletionTarget.Category(subs.id, category.key)) },
                        {
                            showActions = false
                            retainForRemoval = true
                        }) {
                        vm.deleteCategory(subs)
                        showToast(getString(Res.string.delete_success))
                    }
                },
            )
        }

    }
}
