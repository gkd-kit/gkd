package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.domain.rule.RuleConfigIndex
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleFocusNotice
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.rememberRuleListFocus


@Serializable
data class SubsGlobalGroupListRoute(val subsItemId: Long, val focusGroupKey: Int? = null) : NavKey

@Composable
fun SubsGlobalGroupListPage(route: SubsGlobalGroupListRoute) {
    val subsItemId = route.subsItemId
    val focusGroupKey = route.focusGroupKey

    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { SubsGlobalGroupListVm(route) }
    val scope = vm.scope
    val environment = rememberRuleControlEnvironment()
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()

    GkSubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val editable = subsItemId < 0
        val controls = remember(state, environment) { subs.globalGroups.associate { group ->
            group.key to environment.resolve(subs, group, null, state.configs, configIndex)
        } }
        val globalGroups = subs.globalGroups

        val selectionState = rememberMultiSelectionState<Int>()
        val selectableKeys = remember(controls) { controls.filterValues { it.canEnable }.keys }
        val selectedKeys = selectionState.selectedKeys intersect selectableKeys
        val isSelectedMode = selectionState.active
        LaunchedEffect(selectableKeys) {
            selectionState.retain(selectableKeys)
        }
        BackHandler(isSelectedMode) {
            selectionState.clear()
        }

        val updateSelected: (RuleSetting) -> Unit = { setting ->
        val enabled = setting.value
            val keysToUpdate = selectedKeys
            if (keysToUpdate.isNotEmpty()) {
                val request = vm.prepareSwitches(state, keysToUpdate)
                scope.launchUi {
                    vm.runBatchAction {
                        val action = when (enabled) {
                            false -> UiStrings.action_close
                            true -> UiStrings.action_enable
                            null -> UiStrings.setting_follow_default
                        }
                        if (!mainVm.dialogRequests.confirm(
                            title = UiStrings.action_notice,
                            text = UiStrings.global_rule_batch_setting_confirmation(keysToUpdate.size, action),
                        )) return@runBatchAction
                        toast(vm.applySwitches(request, RuleSetting.from(enabled)).description)
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
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = isSelectedMode,
                    selectedCount = selectedKeys.size,
                    onExitSelection = selectionState::clear,
                    scrollBehavior = scrollBehavior,
                    onNavigateBack = { mainVm.popPage() },
                    onTitleClick = pageScrollState::resetScroll,
                    title = {
                        GkTwoLineText(
                            title = subs.name,
                            subtitle = UiStrings.global_rules,
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
                                        text = UiStrings.action_delete,
                                        onDismiss = dismiss,
                                        onClick = {
                                            val keysToDelete = selectedKeys
                                            scope.launchUi {
                                                vm.runBatchAction {
                                                    if (!mainVm.dialogRequests.confirm(
                                                        title = UiStrings.rule_delete,
                                                        text = UiStrings.rule_groups_delete_confirmation(keysToDelete.size),
                                                        error = true,
                                                    )) return@runBatchAction
                                                    val deletedSize = vm.deleteSelectedGroups(keysToDelete)
                                                    selectionState.removeDeleted(keysToDelete)
                                                    toast(if (deletedSize > 0) UiStrings.rule_groups_deleted_count(deletedSize) else UiStrings.selected_rules_changed)
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
                            mainVm.navigatePage(
                                UpsertRuleGroupRoute(
                                    subsId = subsItemId,
                                    groupKey = null,
                                    appId = null,
                                )
                            )
                        },
                        imageVector = GkIcons.Add,
                        contentDescription = UiStrings.rule_add
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
                                mainVm.showRuleGroup(
                                    subscriptionId = subs.id,
                                    appId = null,
                                    group = group,
                                )
                            },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(group.key))
                                scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
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
                            GkEmptyState(text = if (subs.globalGroups.isEmpty()) UiStrings.rules_empty else UiStrings.rules_no_filter_matches)
                        } else {
                            GkPageBottomSpace()
                        }
                    }
                }
            }
        }
    }
}
