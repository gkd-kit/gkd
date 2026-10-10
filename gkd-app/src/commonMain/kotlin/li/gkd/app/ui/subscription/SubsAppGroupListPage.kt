package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.rule_focus_missing
import li.gkd.app.resources.rule_category_uncategorized
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_copy
import li.gkd.app.resources.action_delete
import li.gkd.app.resources.action_enable
import li.gkd.app.resources.action_notice
import li.gkd.app.resources.app_rule_partial_disable_remove
import li.gkd.app.resources.app_rule_partial_disable_remove_confirm
import li.gkd.app.resources.app_rule_restriction_remove
import li.gkd.app.resources.app_rule_whitelist_remove_confirm
import li.gkd.app.resources.app_rule_whitelist_remove_follow_confirm
import li.gkd.app.resources.rule_add
import li.gkd.app.resources.rule_batch_setting_confirmation_prefix
import li.gkd.app.resources.rule_clear_custom_settings
import li.gkd.app.resources.rule_clear_custom_settings_description
import li.gkd.app.resources.rule_custom_settings_description
import li.gkd.app.resources.rule_delete
import li.gkd.app.resources.rule_enable_in_app
import li.gkd.app.resources.rule_groups_delete_confirmation
import li.gkd.app.resources.rule_groups_deleted_count
import li.gkd.app.resources.rules_empty
import li.gkd.app.resources.rules_no_filter_matches
import li.gkd.app.resources.selected_rules_changed
import li.gkd.app.resources.whitelist_remove
import li.gkd.app.rule.RuleDisplayMode
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkAppRuleRestrictionCard
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkRuleSectionHeader
import li.gkd.app.ui.component.GkRuleDisplayIconButton
import li.gkd.app.ui.component.GkRuleCategoryTabs
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleEnableControl
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkRuleListItem
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.RulePropertyText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.rememberRuleListFocus
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.copyText
import li.gkd.db.RuleGroupType
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsAppGroupListPage(
    route: SubsAppGroupListRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val subsItemId = route.subsItemId
    val appId = route.appId
    val focusGroupKey = route.focusGroupKey

    val vm = viewModel { SubsAppGroupListViewModel(route) }
    val scope = vm.scope
    val environment = rememberRuleControlEnvironment()
    val whitelist by SettingsRepository.blockMatchAppList.collectAsStateWithLifecycle()
    val a11yWhitelist by SettingsRepository.blockA11yAppList.collectAsStateWithLifecycle()
    val restrictionSettings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val byCategory = restrictionSettings.appRuleDisplayMode == RuleDisplayMode.ByCategory.value
    val whitelisted = appId in whitelist
    val partialFollowsWhitelist =
        restrictionSettings.enableBlockA11yAppList && restrictionSettings.blockA11yAppListFollowMatch
    val partialDisabled =
        restrictionSettings.enableBlockA11yAppList && !restrictionSettings.blockA11yAppListFollowMatch && appId in a11yWhitelist
    val showAppRestriction = whitelisted || partialDisabled
    val showAppSetting by vm.showAppSetting.collectAsStateWithLifecycle()
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()
    GkSubscriptionPageContent(vm.uiState, mainVm.navigator::pop) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val app = state.app
        val appExists = subs.apps.any { it.id == appId }
        val appControl = environment.app(subs.id, appId, state.configs, configIndex)
        val setApp: (RuleSetting) -> Unit = { setting ->
            val request = vm.prepareAppSwitch(state)
            scope.launchUi {
                vm.applySwitches(request, setting).failureMessage()
                    ?.let { ToastUtils.show(it) }
            }
        }
        if (showAppSetting && appExists) {
            GkRuleSettingsSheet(
                title = app.name ?: appId, subtitle = appControl.scope.label,
                onDismissRequest = { vm.setShowAppSetting(false) }) {
                GkRuleSettingsContent(
                    appControl,
                    setApp,
                    title = stringResource(Res.string.rule_enable_in_app)
                )
            }
        }
        val controls = remember(state, environment) {
            app.groups.associate { group ->
                group.key to environment.resolve(subs, group, appId, state.configs, configIndex)
            }
        }
        val groups = app.groups
        val categorySelection by vm.categorySelection.collectAsStateWithLifecycle()
        val categorySections = remember(subs, groups) {
            buildAppRuleSections(listOf(subs to groups), true)
        }
        val categories = remember(categorySections) {
            categorySections.filter { it.showHeader }.map { it.categoryName }
        }
        val tabCategory = resolveAppRuleCategory(
            categorySelection, categorySections,
            focusGroupKey?.let { Triple(subsItemId, RuleGroupType.App, it) },
        )
        val selectedCategory = tabCategory.takeIf { byCategory }
        val selectedTabIndex = tabCategory?.let { categories.indexOf(it.name) + 1 } ?: 0
        val showCategoryHeaders = selectedCategory == null
        val sections = remember(subs, groups, categorySections, byCategory, selectedCategory) {
            if (byCategory) filterAppRuleSections(categorySections, selectedCategory)
            else buildAppRuleSections(listOf(subs to groups), false)
        }
        val editable = subsItemId < 0
        val selectionState = rememberMultiSelectionState<Int>()
        val selectableKeys = remember(controls, sections) {
            sections.flatMap { it.subscriptions }.flatMap { it.second }
                .filter { controls.getValue(it.key).canEnable }.mapTo(mutableSetOf()) { it.key }
        }
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
                scope.launchUi {
                    vm.runBatchAction {
                        val action = when (enabled) {
                            false -> getString(Res.string.action_close)
                            true -> getString(Res.string.action_enable)
                            null -> getString(Res.string.rule_clear_custom_settings)
                        }
                        if (!mainVm.dialogRequests.confirm(
                                title = getString(Res.string.action_notice),
                                text = getString(
                                    Res.string.rule_batch_setting_confirmation_prefix,
                                    keysToUpdate.size.toString(),
                                    action
                                ) +
                                        if (enabled == null) getString(Res.string.rule_clear_custom_settings_description)
                                        else getString(Res.string.rule_custom_settings_description),
                            )
                        ) return@runBatchAction
                        ToastUtils.show(
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
        val itemKeys = remember(sections, appExists, showAppRestriction, showCategoryHeaders) {
            buildList {
                if (showAppRestriction) add("app-restrictions")
                if (appExists) add("app-switch")
                sections.forEach { section ->
                    if (section.showHeader && showCategoryHeaders) add(section.key)
                    section.subscriptions.forEach { (_, sectionGroups) ->
                        addAll(sectionGroups.map { it.key })
                    }
                }
            }
        }
        val focus = rememberRuleListFocus(
            requestKey = focusGroupKey.takeIf { categorySelection == AppRuleCategorySelection.FocusedRule },
            onTargetMissing = { ToastUtils.show(getString(Res.string.rule_focus_missing)) },
            scrollState = pageScrollState,
            itemKeys = itemKeys,
            targetExists = app.groups.any { it.key == focusGroupKey },
            stickyHeaderKeys = remember(sections, showCategoryHeaders) {
                sections.filter { it.showHeader && showCategoryHeaders }.mapTo(mutableSetOf()) { it.key }
            },
        )
        pageScrollState.ResetOnChange(app.groups.isEmpty(), selectedCategory, enabled = !focus.pending)
        GkScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Column {
                    GkMultiSelectionTopAppBar(
                        selectedMode = isSelectedMode,
                        selectedCount = selectedKeys.size,
                        onExitSelection = selectionState::clear,
                        scrollBehavior = scrollBehavior,
                        onNavigateBack = { mainVm.navigator.pop() },
                        onTitleClick = pageScrollState::resetScroll,
                        title = {
                            GkTwoLineText(
                                title = subs.name,
                                subtitle = appId,
                                showApp = true,
                                appContent = { id, name -> GkAppNameText(id, name) },
                                appFallbackName = app.name,
                            )
                        },
                        actions = { selectedMode ->
                            if (selectedMode) {
                                GkMultiSelectionActions(
                                    selectionState = selectionState,
                                    keys = selectableKeys,
                                    enabled = !batchBusy,
                                ) { dismiss ->
                                    GkBatchActionMenuItem(
                                        text = stringResource(Res.string.action_copy),
                                        onDismiss = dismiss,
                                        onClick = {
                                            val keysToCopy = selectedKeys
                                            scope.launchUi {
                                                vm.runBatchAction {
                                                    copyText(vm.buildSelectedGroupsText(keysToCopy))
                                                }
                                            }
                                        },
                                    )
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
                                                scope.launchUi {
                                                    vm.runBatchAction {
                                                        if (!mainVm.dialogRequests.confirm(
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
                                                        ToastUtils.show(
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
                            } else {
                                GkRuleDisplayIconButton(byCategory) {
                                    SettingsRepository.updateSettings {
                                        it.copy(
                                            appRuleDisplayMode = if (it.appRuleDisplayMode == RuleDisplayMode.ByCategory.value)
                                                RuleDisplayMode.Flat.value else RuleDisplayMode.ByCategory.value,
                                        )
                                    }
                                }
                            }
                        },
                    )
                    GkRuleCategoryTabs(
                        categories, selectedTabIndex,
                        visible = byCategory && categories.isNotEmpty(),
                    ) { index ->
                        vm.selectCategory(
                            if (index == 0) AppRuleCategorySelection.All
                            else AppRuleCategorySelection.Category(categories[index - 1])
                        )
                    }
                }
            },
            floatingActionButton = {
                if (editable) {
                    GkAnimatedFloatingActionButton(
                        visible = !isSelectedMode,
                        onClick = {
                            mainVm.navigator.navigate(
                                UpsertRuleGroupRoute(
                                    subsId = subsItemId,
                                    groupKey = null,
                                    appId = appId
                                )
                            )
                        },
                        contentDescription = stringResource(Res.string.rule_add),
                        imageVector = GkIcons.Add,
                    )
                }
            }) { contentPadding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding).fillMaxSize(),
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
                    }
                }
                if (appExists) {
                    item("app-switch") {
                        GkRuleListItem(
                            onClick = { if (!isSelectedMode) vm.setShowAppSetting(true) },
                            trailing = {
                                if (!isSelectedMode) GkRuleEnableControl(
                                    appControl, setApp, modifier = it,
                                    identity = RuleSwitchTarget.App(
                                        subsItemId,
                                        appId
                                    )
                                )
                            }) {
                            Text(
                                stringResource(Res.string.rule_enable_in_app),
                                style = MaterialTheme.typography.titleSmall
                            )
                            val restriction =
                                RulePropertyText.restrictionSummary(appControl)
                            if (restriction != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    restriction, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                sections.forEach { section ->
                    if (section.showHeader && showCategoryHeaders) {
                        stickyHeader(section.key) {
                            GkRuleSectionHeader(
                                title = section.categoryName
                                    ?: stringResource(Res.string.rule_category_uncategorized),
                                groupCount = section.groupCount,
                            )
                        }
                    }
                    items(section.subscriptions.single().second, { it.key }) { group ->
                        GkRuleGroupCard(
                            modifier = Modifier.animateListItem(),
                            subs = subs,
                            appId = appId,
                            group = group,
                            hideCategoryPrefix = section.type == AppRuleSectionType.Category,
                            control = controls.getValue(group.key),
                            onOpen = {
                                mainVm.showRuleGroup(subs.id, appId, group, appId)
                            },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(group.key))
                                scope.launchUi {
                                    vm.applySwitches(request, setting)
                                        .failureMessage()?.let { ToastUtils.show(it) }
                                }
                            },
                            highlighted = !isSelectedMode && focus.highlightedKey == group.key,
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
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (groups.isEmpty()) {
                        GkEmptyState(
                            text = if (app.groups.isEmpty()) stringResource(Res.string.rules_empty) else stringResource(
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
