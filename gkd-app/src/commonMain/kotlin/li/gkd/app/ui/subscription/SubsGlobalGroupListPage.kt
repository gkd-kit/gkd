package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_delete
import li.gkd.app.resources.action_enable
import li.gkd.app.resources.action_notice
import li.gkd.app.resources.global_rule_batch_setting_confirmation
import li.gkd.app.resources.global_rules
import li.gkd.app.resources.rule_add
import li.gkd.app.resources.rule_delete
import li.gkd.app.resources.rule_groups_delete_confirmation
import li.gkd.app.resources.rule_groups_deleted_count
import li.gkd.app.resources.rules_empty
import li.gkd.app.resources.rules_no_filter_matches
import li.gkd.app.resources.selected_rules_changed
import li.gkd.app.resources.setting_follow_default
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleSetting
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleFocusNotice
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.rememberRuleListFocus
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.ShowRuleGroup
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsGlobalGroupListPage(
    route: SubsGlobalGroupListRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
    showRuleGroup: ShowRuleGroup,
) {
    val subsItemId = route.subsItemId
    val focusGroupKey = route.focusGroupKey

    val vm = viewModel { SubsGlobalGroupListViewModel(route) }
    val scope = vm.scope
    val environment = rememberRuleControlEnvironment()
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()

    GkSubscriptionPageContent(vm.uiState, onBack) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val editable = subsItemId < 0
        val controls = remember(state, environment) {
            subs.globalGroups.associate { group ->
                group.key to environment.resolve(subs, group, null, state.configs, configIndex)
            }
        }
        val globalGroups = subs.globalGroups

        val selectionState = rememberMultiSelectionState<Int>()
        val selectableKeys = remember(controls) { controls.filterValues { it.canEnable }.keys }
        val selectedKeys = selectionState.selectedKeys intersect selectableKeys
        val isSelectedMode = selectionState.active
        LaunchedEffect(selectableKeys) {
            selectionState.retain(selectableKeys)
        }
        GkBackHandler(isSelectedMode) {
            selectionState.clear()
        }

        val updateSelected: (RuleSetting) -> Unit = { setting ->
            val enabled = setting.value
            val keysToUpdate = selectedKeys
            if (keysToUpdate.isNotEmpty()) {
                val request = vm.prepareSwitches(state, keysToUpdate)
                launchUi(scope, showToast) {
                    vm.runBatchAction {
                        val action = when (enabled) {
                            false -> getString(Res.string.action_close)
                            true -> getString(Res.string.action_enable)
                            null -> getString(Res.string.setting_follow_default)
                        }
                        if (!dialogs.confirm(
                                title = getString(Res.string.action_notice),
                                text = getString(
                                    Res.string.global_rule_batch_setting_confirmation,
                                    keysToUpdate.size.toString(),
                                    action
                                ),
                            )
                        ) return@runBatchAction
                        showToast(
                            vm.applySwitches(request, RuleSetting.from(enabled))
                                .description()
                        )
                    }
                }
            }
        }
        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        val itemKeys = remember(globalGroups) { globalGroups.map { it.key } }
        val focus = rememberRuleListFocus(
            requestKey = focusGroupKey,
            scrollState = pageScrollState,
            itemKeys = itemKeys,
            targetExists = subs.globalGroups.any { it.key == focusGroupKey },
        )
        pageScrollState.ResetOnChange(globalGroups.isEmpty(), enabled = !focus.pending)
        GkScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = isSelectedMode,
                    selectedCount = selectedKeys.size,
                    onExitSelection = selectionState::clear,
                    scrollBehavior = scrollBehavior,
                    onNavigateBack = { onBack() },
                    onTitleClick = pageScrollState::resetScroll,
                    title = {
                        GkTwoLineText(
                            title = subs.name,
                            subtitle = stringResource(Res.string.global_rules),
                        )
                    },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(
                                selectionState = selectionState,
                                keys = selectableKeys,
                                enabled = !batchBusy,
                            ) { dismiss ->
                                GkRuleBatchMenuItems(
                                    enabled = !batchBusy,
                                    onDismiss = dismiss,
                                    onUpdate = updateSelected,
                                )
                                if (editable) {
                                    GkBatchActionMenuItem(
                                        text = stringResource(Res.string.action_delete),
                                        onDismiss = dismiss,
                                        onClick = {
                                            val keysToDelete = selectedKeys
                                            launchUi(scope, showToast) {
                                                vm.runBatchAction {
                                                    if (!dialogs.confirm(
                                                            title = getString(Res.string.rule_delete),
                                                            text = getString(
                                                                Res.string.rule_groups_delete_confirmation,
                                                                keysToDelete.size.toString()
                                                            ),
                                                            error = true,
                                                        )
                                                    ) return@runBatchAction
                                                    val deletedSize =
                                                        vm.deleteSelectedGroups(keysToDelete)
                                                    selectionState.removeDeleted(keysToDelete)
                                                    showToast(
                                                        if (deletedSize > 0) getString(
                                                            Res.string.rule_groups_deleted_count,
                                                            deletedSize.toString()
                                                        ) else getString(Res.string.selected_rules_changed)
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (editable) {
                    GkAnimatedFloatingActionButton(
                        visible = !isSelectedMode,
                        onClick = {
                            onNavigate(
                                UpsertRuleGroupRoute(
                                    subsId = subsItemId,
                                    groupKey = null,
                                    appId = null,
                                )
                            )
                        },
                        imageVector = GkIcons.Add,
                        contentDescription = stringResource(Res.string.rule_add)
                    )
                }
            },
        ) { paddingValues ->
            Column(Modifier.scaffoldPadding(paddingValues)) {
                if (focus.missing) GkRuleFocusNotice()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                ) {
                    items(globalGroups, { g -> g.key }) { group ->
                        GkRuleGroupCard(
                            modifier = Modifier.animateListItem(),
                            subs = subs,
                            appId = null,
                            group = group,
                            highlighted = !isSelectedMode && focus.highlightedKey == group.key,
                            control = controls.getValue(group.key),
                            onOpen = {
                                showRuleGroup(subs.id, null, group, null)
                            },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(group.key))
                                launchUi(scope, showToast) {
                                    vm.applySwitches(request, setting)
                                        .failureMessage()?.let { showToast(it) }
                                }
                            },
                            isSelectedMode = isSelectedMode,
                            selectionEnabled = !batchBusy,
                            isSelected = group.key in selectedKeys,
                            onLongClick = {
                                if (!batchBusy) {
                                    selectionState.select(group.key)
                                }
                            },
                            onSelectedChange = {
                                selectionState.toggle(group.key)
                            }
                        )
                    }
                    item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                        if (globalGroups.isEmpty()) {
                            GkEmptyState(
                                text = if (subs.globalGroups.isEmpty()) stringResource(Res.string.rules_empty) else stringResource(
                                    Res.string.rules_no_filter_matches
                                )
                            )
                        } else {
                            GkPageBottomSpace()
                        }
                    }
                }
            }
        }
    }
}
