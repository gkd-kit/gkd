package li.gkd.app.ui

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import li.gkd.app.ui.component.GkAppRuleRestrictionCard
import li.gkd.app.store.AppStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.core.state.Loadable
import li.gkd.app.domain.rule.RuleConfigIndex
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.toRuleGroupTarget
import li.gkd.app.feature.log.ActionLogRoute
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.UpsertRuleGroupRoute
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.RuleSortOption
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.collator
import li.gkd.app.util.findOption
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.db.ActionLog
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkRuleListHeader
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMenuGroupCard
import li.gkd.app.ui.component.GkMenuItemCheckbox
import li.gkd.app.ui.component.GkMenuItemRadioButton
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleFocusNotice
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.rememberRuleListFocus

@Serializable
data class AppConfigRoute(
    val appId: String,
    val focusLog: ActionLog? = null,
) : NavKey

@Composable
fun AppConfigPage(route: AppConfigRoute) {
    val appId = route.appId
    val focusLog = route.focusLog
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { AppConfigVm(route) }
    val scope = vm.scope
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()

    val store by storeFlow.collectAsStateWithLifecycle()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val configIndex = remember(state?.configs) { state?.configs?.let(::RuleConfigIndex) }
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val environment = rememberRuleControlEnvironment()
    val whitelist by AppStore.blockMatchAppListFlow.collectAsStateWithLifecycle()
    val a11yWhitelist by AppStore.blockA11yAppListFlow.collectAsStateWithLifecycle()
    val whitelisted = appId in whitelist
    val partialFollowsWhitelist = store.enableBlockA11yAppList && store.blockA11yAppListFollowMatch
    val partialDisabled = store.enableBlockA11yAppList && !store.blockA11yAppListFollowMatch && appId in a11yWhitelist
    val showAppRestriction = whitelisted || partialDisabled
    var revealDisabledRules by rememberSaveable(focusLog) { mutableStateOf(false) }
    val showDisabledRules = store.showDisabledRule || revealDisabledRules
    val controls = remember(state, environment) { state?.subsPairs.orEmpty().flatMap { (entry, groups) ->
        groups.map { group -> group.toRuleGroupTarget(entry.subsItem.id, appId) to
            environment.resolve(entry.subscription, group, appId, checkNotNull(state).configs,
                checkNotNull(configIndex)) }
    }.toMap() }
    val subsPairs = remember(state, showDisabledRules, controls, store.appRuleSort) {
        state?.subsPairs.orEmpty().mapNotNull { (entry, groups) ->
            val visible = groups.filter { group ->
                val control = controls.getValue(group.toRuleGroupTarget(entry.subsItem.id, appId))
                // App-wide whitelist membership does not turn the rule's own switch off.
                showDisabledRules || (control.configuredEnabled && control.canEnable && control.restrictions.isEmpty())
            }
            val sorted = when (RuleSortOption.objects.findOption(store.appRuleSort)) {
                RuleSortOption.ByDefault -> visible
                RuleSortOption.ByRuleName -> visible.sortedWith { a, b -> collator.compare(a.name, b.name) }
                RuleSortOption.ByActionTime -> visible.sortedByDescending { group -> state?.latestLogs?.find {
                    it.subsId == entry.subsItem.id && it.groupType == group.groupType && it.groupKey == group.key
                }?.id ?: 0 }
            }
            (entry to sorted).takeIf { sorted.isNotEmpty() }
        }
    }
    val groupSize = subsPairs.sumOf { it.second.size }

    val selectableTargets = remember(subsPairs, appId, controls) {
        subsPairs.flatMap { (entry, groups) ->
            groups.map { group -> group.toRuleGroupTarget(entry.subsItem.id, appId) }
        }.filterTo(mutableSetOf()) { controls.getValue(it).canEnable }
    }
    val selectionState = rememberMultiSelectionState<RuleGroupTarget>()
    val selectedDataSet = selectionState.selectedKeys intersect selectableTargets
    val isSelectedMode = selectionState.active
    LaunchedEffect(selectableTargets, loadableState) {
        if (loadableState is Loadable.Ready) selectionState.retain(selectableTargets)
    }
    BackHandler(isSelectedMode) {
        selectionState.clear()
    }

    val updateSelected: (RuleSetting) -> Unit = { setting ->
        val enabled = setting.value
        val targets = selectedDataSet
        if (targets.isNotEmpty() && state != null) {
            val request = vm.prepareSwitches(state, targets)
            scope.launchUi {
                vm.runBatchAction {
                    val action = when (enabled) {
                        false -> UiStrings.action_close
                        true -> UiStrings.action_enable
                        null -> UiStrings.setting_follow_default
                    }
                    if (!mainVm.dialogRequests.confirm(
                        title = UiStrings.action_notice,
                        text = UiStrings.app_rules_batch_setting_confirmation(targets.size, action),
                    )) return@runBatchAction
                    toast(vm.applySwitches(request, RuleSetting.from(enabled)).description)
                }
            }
        }
    }
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    val focusKey = remember(focusLog) { focusLog?.let { Triple(it.subsId, it.groupType, it.groupKey) } }
    val itemKeys = remember(subsPairs, showAppRestriction) { buildList {
        if (showAppRestriction) add("app-restrictions")
        subsPairs.forEach { (entry, groups) ->
            add(entry.subsItem.id)
            groups.forEach { add(Triple(entry.subsItem.id, it.groupType, it.key)) }
        }
    } }
    val focus = rememberRuleListFocus(
        requestKey = focusKey,
        scrollState = pageScrollState,
        itemKeys = itemKeys,
        ready = loadableState is Loadable.Ready,
        targetExists = state?.subsPairs.orEmpty().any { (entry, groups) ->
            groups.any { Triple(entry.subsItem.id, it.groupType, it.key) == focusKey }
        },
        stickyHeaderKeys = remember(subsPairs) { subsPairs.mapTo(mutableSetOf()) { it.first.subsItem.id } },
        onRevealTarget = { revealDisabledRules = true },
    )
    pageScrollState.ResetOnChange(
        groupSize > 0,
        firstLoading,
        enabled = !focus.pending,
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = isSelectedMode,
                selectedCount = selectedDataSet.size,
                onExitSelection = selectionState::clear,
                scrollBehavior = scrollBehavior,
                onNavigateBack = { mainVm.popPage() },
                onTitleClick = pageScrollState::resetScroll,
                title = {
                    GkAppNameText(appId = appId)
                },
                actions = { selectedMode ->
                    if (selectedMode) {
                        GkMultiSelectionActions(
                            selectionState = selectionState,
                            keys = selectableTargets,
                            enabled = !batchBusy && loadableState is Loadable.Ready,
                        ) { dismiss ->
                            GkBatchActionMenuItem(
                                text = UiStrings.action_copy,
                                enabled = selectedDataSet.any { it is RuleGroupTarget.App },
                                onDismiss = dismiss,
                                onClick = {
                                    val targets = selectedDataSet.filterIsInstance<RuleGroupTarget.App>().toSet()
                                    scope.launchUi {
                                        vm.runBatchAction {
                                            copyText(vm.buildSelectedGroupsText(targets))
                                        }
                                    }
                                },
                            )
                            GkRuleBatchMenuItems(
                                enabled = true,
                                onDismiss = dismiss,
                                onUpdate = updateSelected,
                            )
                        }
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            GkFilterIconButton(
                                filtered = state != null && groupSize < state.subsPairs.sumOf { it.second.size },
                                onClick = { expanded = true },
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                GkMenuGroupCard(inTop = true, title = UiStrings.sort_title) {
                                    val handleItem: (RuleSortOption) -> Unit = throttle(vm::setRuleSortType)
                                    RuleSortOption.objects.forEach { option ->
                                        GkMenuItemRadioButton(
                                            text = option.label,
                                            selected = RuleSortOption.objects.findOption(store.appRuleSort) == option,
                                            onClick = { handleItem(option) },
                                        )
                                    }
                                }
                                GkMenuGroupCard(title = UiStrings.filter_title) {
                                    GkMenuItemCheckbox(
                                        text = UiStrings.not_enabled,
                                        checked = showDisabledRules,
                                        onClick = {
                                            vm.setShowDisabledRules(!showDisabledRules)
                                            revealDisabledRules = false
                                        },
                                    )
                                }
                            }
                        }
                        GkIconButton(
                            imageVector = GkIcons.History,
                            onClick = throttle {
                                mainVm.navigatePage(ActionLogRoute(appId = appId))
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            GkAnimatedFloatingActionButton(
                visible = !isSelectedMode,
                onClick = {
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = LOCAL_SUBS_ID,
                            groupKey = null,
                            appId = appId
                        )
                    )
                },
                imageVector = GkIcons.Add,
                contentDescription = UiStrings.rule_add
            )
        },
    ) { contentPadding ->
        Column(Modifier.scaffoldPadding(contentPadding)) {
            if (focus.missing) GkRuleFocusNotice()
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
            ) {
                if (showAppRestriction) {
                    item("app-restrictions") {
                        GkAppRuleRestrictionCard(
                            whitelisted = whitelisted,
                            partialDisabled = partialDisabled,
                            partialFollowsWhitelist = partialFollowsWhitelist,
                            onRemoveWhitelist = {
                                scope.launchUi {
                                    if (mainVm.dialogRequests.confirm(
                                        title = UiStrings.whitelist_remove,
                                        text = if (partialFollowsWhitelist) UiStrings.app_rule_whitelist_remove_follow_confirm
                                            else UiStrings.app_rule_whitelist_remove_confirm,
                                        confirmText = UiStrings.app_rule_restriction_remove,
                                        dismissOnRequest = true,
                                    )) vm.removeFromWhitelist()
                                }
                            },
                            onRemovePartialDisable = {
                                scope.launchUi {
                                    if (mainVm.dialogRequests.confirm(
                                        title = UiStrings.app_rule_partial_disable_remove,
                                        text = UiStrings.app_rule_partial_disable_remove_confirm,
                                        confirmText = UiStrings.app_rule_restriction_remove,
                                        dismissOnRequest = true,
                                    )) vm.removeFromPartialDisable()
                                }
                            },
                        )
                    }
                }
                subsPairs.forEach { (entry, groups) ->
                    val subsId = entry.subsItem.id
                    stickyHeader(entry.subsItem.id) {
                        GkRuleListHeader(
                            onClick = throttle {
                                mainVm.navigatePage(if (entry.subscription.apps.any { it.id == appId })
                                    SubsAppGroupListRoute(subsId, appId) else li.gkd.app.feature.subscription.SubsGlobalGroupListRoute(subsId))
                            },
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = entry.subscription.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    items(groups, { Triple(subsId, it.groupType, it.key) }) { group ->
                        val isSelected = selectedDataSet.any {
                            it.subsId == subsId && it.groupType == group.groupType && it.groupKey == group.key
                        }
                        val onLongClick = {
                            if (!batchBusy) {
                                selectionState.select(
                                    group.toRuleGroupTarget(
                                        subsId = subsId,
                                        appId = appId,
                                    ),
                                )
                            }
                        }
                        val onSelectedChange = {
                            selectionState.toggle(
                                group.toRuleGroupTarget(
                                    subsId = subsId,
                                    appId = appId,
                                )
                            )
                        }
                        GkRuleGroupCard(
                            modifier = Modifier.animateListItem(),
                            subs = entry.subscription,
                            appId = appId,
                            group = group,
                            control = controls.getValue(group.toRuleGroupTarget(subsId, appId)),
                            onOpen = {
                                mainVm.showRuleGroup(
                                    subscriptionId = subsId,
                                    appId = appId,
                                    group = group,
                                )
                            },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(checkNotNull(state), setOf(group.toRuleGroupTarget(subsId, appId)))
                                scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
                            },
                            onLongClick = onLongClick,
                            isSelectedMode = isSelectedMode,
                            selectionEnabled = !batchBusy,
                            isSelected = isSelected,
                            onSelectedChange = onSelectedChange,
                            highlighted = !isSelectedMode && focus.highlightedKey == Triple(subsId, group.groupType, group.key),
                        )
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (groupSize == 0 && !firstLoading) {
                        GkEmptyState(
                            text = if (loadError != null) {
                                loadError.message ?: UiStrings.data_load_failed
                            } else if (showDisabledRules) {
                                UiStrings.data_empty
                            } else {
                                UiStrings.data_empty_filter_hint
                            }
                        )
                    } else {
                        GkPageBottomSpace()
                    }
                }
            }
        }
    }
}
