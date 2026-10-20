package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import li.gkd.app.ui.component.gkTopAppBarScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.error.messageResource
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleDisplayMode
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkAppRuleRestrictionCard
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkCollapsingRuleList
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMenuGroupCard
import li.gkd.app.ui.component.GkMenuItemCheckbox
import li.gkd.app.ui.component.GkMenuItemRadioButton
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkRuleListHeader
import li.gkd.app.ui.component.GkRuleDisplayIconButton
import li.gkd.app.ui.component.GkRuleCategoryTabs
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.rememberRuleListFocus
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.rule.RuleSort
import li.gkd.app.model.findOption
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.SortUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import li.gkd.db.SubscriptionIds

@Composable
fun AppConfigPage(
    route: AppConfigRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val appId = route.appId
    val focusLog = route.focusLog
    val vm = viewModel { AppConfigViewModel(route) }
    val scope = vm.scope
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()

    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val byCategory = store.appRuleSummaryDisplayMode == RuleDisplayMode.ByCategory.value
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val configIndex = remember(state?.configs) { state?.configs?.let(::RuleConfigIndex) }
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val environment = rememberRuleControlEnvironment()
    val whitelist by SettingsRepository.blockMatchAppList.collectAsStateWithLifecycle()
    val a11yWhitelist by SettingsRepository.blockA11yAppList.collectAsStateWithLifecycle()
    val whitelisted = appId in whitelist
    val partialFollowsWhitelist = store.enableBlockA11yAppList && store.blockA11yAppListFollowMatch
    val partialDisabled =
        store.enableBlockA11yAppList && !store.blockA11yAppListFollowMatch && appId in a11yWhitelist
    val revealDisabledRules by vm.revealDisabledRules.collectAsStateWithLifecycle()
    val showDisabledRules = store.showDisabledRule || revealDisabledRules
    val controls = remember(state, environment) {
        state?.subsPairs.orEmpty().flatMap { (entry, groups) ->
            groups.map { group ->
                group.toRuleGroupTarget(entry.subsItem.id, appId) to
                        environment.resolve(
                            entry.subscription, group, appId, checkNotNull(state).configs,
                            checkNotNull(configIndex)
                        )
            }
        }.toMap()
    }
    val subsPairs = remember(state, showDisabledRules, controls, store.appRuleSort) {
        state?.subsPairs.orEmpty().mapNotNull { (entry, groups) ->
            val visible = groups.filter { group ->
                val control = controls.getValue(group.toRuleGroupTarget(entry.subsItem.id, appId))
                // App-wide whitelist membership does not turn the rule's own switch off.
                showDisabledRules || (control.configuredEnabled && control.canEnable && control.restrictions.isEmpty())
            }
            val sorted = when (RuleSort.entries.findOption(store.appRuleSort)) {
                RuleSort.ByTriggerCount -> visible.sortedByDescending { group ->
                    state?.triggerStats?.group(group.toRuleGroupTarget(entry.subsItem.id, appId))?.count ?: 0L
                }
                RuleSort.ByDefault -> visible
                RuleSort.ByRuleName -> visible.sortedWith { a, b ->
                    SortUtils.collator.compare(
                        a.name,
                        b.name
                    )
                }

                RuleSort.ByActionTime -> visible.sortedByDescending { group ->
                    state?.latestLogs?.find {
                        it.subsId == entry.subsItem.id && it.groupType == group.groupType && it.groupKey == group.key
                    }?.id ?: 0
                }
            }
            (entry to sorted).takeIf { sorted.isNotEmpty() }
        }
    }
    val focusKey =
        remember(focusLog) { focusLog?.let { Triple(it.subsId, it.groupType, it.groupKey) } }
    val categorySelection by vm.categorySelection.collectAsStateWithLifecycle()
    val categorySections = remember(state?.subsPairs) {
        buildAppRuleSections(state?.subsPairs.orEmpty().map { it.first.subscription to it.second }, true)
    }
    val categories = remember(categorySections) {
        categorySections.filter { it.showHeader }.map { it.categoryName }
    }
    val tabCategory = resolveAppRuleCategory(categorySelection, categorySections, focusKey)
    val selectedCategory = tabCategory.takeIf { byCategory }
    val selectedTabIndex = tabCategory?.let { categories.indexOf(it.name) + 1 } ?: 0
    val sections = remember(subsPairs, byCategory, selectedCategory) {
        filterAppRuleSections(
            buildAppRuleSections(
                subsPairs.map { (entry, groups) -> entry.subscription to groups },
                byCategory,
            ),
            selectedCategory,
            globalOnly = byCategory && selectedCategory == null,
        )
    }
    val groupSize = sections.sumOf { it.groupCount }

    val selectableTargets = remember(sections, appId, controls) {
        sections.flatMap { it.subscriptions }.flatMap { (subscription, groups) ->
            groups.map { group -> group.toRuleGroupTarget(subscription.id, appId) }
        }.filterTo(mutableSetOf()) { controls.getValue(it).canEnable }
    }
    val selectionState = vm.selection
    val selectionSnapshot by selectionState.state.collectAsStateWithLifecycle()
    val selectedDataSet = selectionSnapshot.selectedKeys intersect selectableTargets
    val isSelectedMode = selectionSnapshot.active
    LaunchedEffect(selectableTargets, loadableState) {
        if (loadableState is Loadable.Ready) selectionState.retain(selectableTargets)
    }
    GkBackHandler(isSelectedMode) {
        selectionState.clear()
    }

    val updateSelected: (RuleSetting) -> Unit = { setting ->
        val enabled = setting.value
        if (selectedDataSet.isNotEmpty() && state != null) {
            val request = vm.prepareSwitches(state, selectedDataSet)
            scope.launchUi {
                vm.runBatchAction {
                    val action = when (enabled) {
                        false -> getString(Res.string.action_close)
                        true -> getString(Res.string.action_enable)
                        null -> getString(Res.string.setting_follow_default)
                    }
                    vm.applySwitches(
                        request, setting,
                        confirmation = getString(
                            Res.string.app_rules_batch_setting_confirmation,
                            selectedDataSet.size.toString(),
                            action
                        ),
                    )?.let { ToastUtils.show(it.description()) }
                }
            }
        }
    }
    val headerState = rememberTopAppBarState(initialHeightOffsetLimit = 0f)
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    val itemKeys = remember(sections) {
        buildList {
            sections.forEach { section ->
                section.subscriptions.forEach { (subscription, groups) ->
                    add(section.subscriptionKey(subscription.id))
                    groups.forEach { add(Triple(subscription.id, it.groupType, it.key)) }
                }
            }
        }
    }
    val focus = rememberRuleListFocus(
        requestKey = focusKey.takeIf { categorySelection == AppRuleCategorySelection.FocusedRule },
        onTargetMissing = { ToastUtils.show(getString(Res.string.rule_focus_missing)) },
        scrollState = pageScrollState,
        onBeforePosition = { headerState.heightOffset = headerState.heightOffsetLimit },
        itemKeys = itemKeys,
        ready = loadableState is Loadable.Ready,
        targetExists = state?.subsPairs.orEmpty().any { (entry, groups) ->
            groups.any { Triple(entry.subsItem.id, it.groupType, it.key) == focusKey }
        },
        stickyHeaderKeys = remember(sections) {
            sections.flatMap { section ->
                section.subscriptions.map { section.subscriptionKey(it.first.id) }
            }.toSet()
        },
        onRevealTarget = { vm.setRevealDisabledRules(true) },
    )
    pageScrollState.ResetOnChange(
        groupSize > 0,
        firstLoading,
        selectedCategory,
        showDisabledRules,
        enabled = !focus.pending,
    )

    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = isSelectedMode,
                selectedCount = selectedDataSet.size,
                onExitSelection = selectionState::clear,
                scrollBehavior = scrollBehavior,
                onNavigateBack = { mainVm.navigator.pop() },
                onTitleClick = {
                    headerState.heightOffset = 0f
                    pageScrollState.resetScroll()
                },
                title = {
                    GkAppNameText(appId = appId)
                },
                actions = { selectedMode ->
                    if (selectedMode) {
                        GkMultiSelectionActions(
                            selected = selectionSnapshot.selectedKeys,
                            keys = selectableTargets,
                            onSelectAll = selectionState::selectAll,
                            onInvert = selectionState::invert,
                            enabled = !batchBusy && loadableState is Loadable.Ready,
                        ) { dismiss ->
                            GkBatchActionMenuItem(
                                text = stringResource(Res.string.action_copy),
                                enabled = selectedDataSet.any { it is RuleGroupTarget.App },
                                onDismiss = dismiss,
                                onClick = {
                                    val targets =
                                        selectedDataSet.filterIsInstance<RuleGroupTarget.App>()
                                            .toSet()
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
                        GkRuleDisplayIconButton(byCategory) {
                            SettingsRepository.updateSettings {
                                it.copy(
                                    appRuleSummaryDisplayMode = RuleDisplayMode.switch(it.appRuleSummaryDisplayMode),
                                )
                            }
                        }
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            GkFilterIconButton(
                                filtered = state != null && subsPairs.sumOf { it.second.size } < state.subsPairs.sumOf { it.second.size },
                                onClick = { expanded = true },
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                GkMenuGroupCard(
                                    inTop = true,
                                    title = stringResource(Res.string.sort_title)
                                ) {
                                    val handleItem: (RuleSort) -> Unit = vm::setRuleSortType
                                    RuleSort.entries.forEach { option ->
                                        GkMenuItemRadioButton(
                                            text = stringResource(option.labelResource),
                                            selected = RuleSort.entries.findOption(store.appRuleSort) == option,
                                            onClick = { handleItem(option) },
                                        )
                                    }
                                }
                                GkMenuGroupCard(title = stringResource(Res.string.display_title)) {
                                    GkMenuItemCheckbox(
                                        text = stringResource(Res.string.not_enabled),
                                        checked = showDisabledRules,
                                        onClick = {
                                            vm.setShowDisabledRules(!showDisabledRules)
                                            vm.setRevealDisabledRules(false)
                                        },
                                    )
                                }
                            }
                        }
                        GkIconButton(
                            imageVector = GkIcons.History,
                            onClick = {
                                mainVm.navigator.navigate(ActionLogRoute(appId = appId))
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
                    mainVm.navigator.navigate(
                        UpsertRuleGroupRoute(
                            subsId = SubscriptionIds.LOCAL,
                            groupKey = null,
                            appId = appId
                        )
                    )
                },
                imageVector = GkIcons.Add,
                contentDescription = stringResource(Res.string.rule_add)
            )
        },
    ) { contentPadding ->
        GkCollapsingRuleList(
            headerState = headerState,
            modifier = Modifier.scaffoldPadding(contentPadding),
            header = {
                GkAppRuleRestrictionCard(
                    whitelisted = whitelisted,
                    partialDisabled = partialDisabled,
                    partialFollowsWhitelist = partialFollowsWhitelist,
                    onRemoveWhitelist = {
                        scope.launchUi {
                            if (mainVm.dialogRequests.confirm(
                                    title = getString(Res.string.whitelist_remove),
                                    text = if (partialFollowsWhitelist) getString(Res.string.app_rule_whitelist_remove_follow_confirm)
                                    else getString(Res.string.app_rule_whitelist_remove_confirm),
                                    confirmText = getString(Res.string.app_rule_restriction_remove),
                                    dismissOnRequest = true,
                                )
                            ) vm.removeFromWhitelist()
                        }
                    },
                    onRemovePartialDisable = {
                        scope.launchUi {
                            if (mainVm.dialogRequests.confirm(
                                    title = getString(Res.string.app_rule_partial_disable_remove),
                                    text = getString(Res.string.app_rule_partial_disable_remove_confirm),
                                    confirmText = getString(Res.string.app_rule_restriction_remove),
                                    dismissOnRequest = true,
                                )
                            ) vm.removeFromPartialDisable()
                        }
                    },
                )
            },
            tabs = {
                GkRuleCategoryTabs(
                    categories, selectedTabIndex,
                    visible = byCategory,
                    firstTabTitle = stringResource(Res.string.global_rules),
                ) { index ->
                    vm.selectCategory(
                        if (index == 0) AppRuleCategorySelection.Global
                        else AppRuleCategorySelection.Category(categories[index - 1])
                    )
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                sections.forEach { section ->
                    section.subscriptions.forEach { (subscription, groups) ->
                        val subsId = subscription.id
                        stickyHeader(section.subscriptionKey(subsId)) {
                            GkRuleListHeader(
                                onClick = {
                                    mainVm.navigator.navigate(
                                        if (section.type == AppRuleSectionType.Global ||
                                            subscription.apps.none { it.id == appId })
                                            SubsGlobalGroupListRoute(subsId)
                                        else SubsAppGroupListRoute(subsId, appId)
                                    )
                                },
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = subscription.name,
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
                                subs = subscription,
                                appId = appId,
                                group = group,
                                triggerSummary = state?.triggerStats?.group(group.toRuleGroupTarget(subsId, appId)),
                                hideCategoryPrefix = section.type == AppRuleSectionType.Category,
                                control = controls.getValue(group.toRuleGroupTarget(subsId, appId)),
                                onOpen = {
                                    mainVm.showRuleGroup(subsId, appId, group, appId)
                                },
                                onSettingChange = { setting ->
                                    val request = vm.prepareSwitches(
                                        checkNotNull(state),
                                        setOf(group.toRuleGroupTarget(subsId, appId))
                                    )
                                    scope.launchUi {
                                        vm.applySwitches(request, setting)
                                            ?.failureMessage()?.let { ToastUtils.show(it) }
                                    }
                                },
                                onLongClick = onLongClick,
                                isSelectedMode = isSelectedMode,
                                selectionEnabled = !batchBusy,
                                isSelected = isSelected,
                                onSelectedChange = onSelectedChange,
                                highlighted = !isSelectedMode && focus.highlightedKey == Triple(
                                    subsId,
                                    group.groupType,
                                    group.key
                                ),
                            )
                        }
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (groupSize == 0 && !firstLoading) {
                        GkEmptyState(
                            text = if (loadError != null) {
                                loadError.messageResource()
                            } else if (showDisabledRules) {
                                stringResource(Res.string.data_empty)
                            } else {
                                stringResource(Res.string.data_empty_filter_hint)
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
