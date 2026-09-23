package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import li.gkd.app.ui.component.GkAppRuleRestrictionCard
import li.gkd.app.store.AppStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkRuleBatchMenuItems
import li.gkd.app.ui.component.GkRuleEnableControl
import li.gkd.app.ui.component.GkRuleFocusNotice
import li.gkd.app.ui.component.GkRuleGroupCard
import li.gkd.app.ui.component.GkRuleListItem
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.RulePropertyText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.rememberRuleListFocus

@Serializable
data class SubsAppGroupListRoute(
    val subsItemId: Long,
    val appId: String,
    val focusGroupKey: Int? = null,
) : NavKey

@Composable
fun SubsAppGroupListPage(route: SubsAppGroupListRoute) {
    val subsItemId = route.subsItemId
    val appId = route.appId
    val focusGroupKey = route.focusGroupKey

    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { SubsAppGroupListVm(route) }
    val scope = vm.scope
    val environment = rememberRuleControlEnvironment()
    val whitelist by AppStore.blockMatchAppListFlow.collectAsStateWithLifecycle()
    val a11yWhitelist by AppStore.blockA11yAppListFlow.collectAsStateWithLifecycle()
    val restrictionSettings by AppStore.storeFlow.collectAsStateWithLifecycle()
    val whitelisted = appId in whitelist
    val partialFollowsWhitelist = restrictionSettings.enableBlockA11yAppList && restrictionSettings.blockA11yAppListFollowMatch
    val partialDisabled = restrictionSettings.enableBlockA11yAppList && !restrictionSettings.blockA11yAppListFollowMatch && appId in a11yWhitelist
    val showAppRestriction = whitelisted || partialDisabled
    var showAppSetting by rememberSaveable { mutableStateOf(false) }
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()
    GkSubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val configIndex = remember(state.configs) { RuleConfigIndex(state.configs) }
        val app = state.app
        val appExists = subs.apps.any { it.id == appId }
        val appControl = environment.app(subs.id, appId, state.configs, configIndex)
        val setApp: (RuleSetting) -> Unit = { setting ->
            val request = vm.prepareAppSwitch(state)
            scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
        }
        if (showAppSetting && appExists) {
            GkRuleSettingsSheet(title = app.name ?: appId, subtitle = appControl.scope,
                onDismissRequest = { showAppSetting = false }) {
                GkRuleSettingsContent(appControl, setApp, title = UiStrings.rule_enable_in_app)
            }
        }
        val controls = remember(state, environment) { app.groups.associate { group ->
            group.key to environment.resolve(subs, group, appId, state.configs, configIndex)
        } }
        val groups = app.groups
        val editable = subsItemId < 0
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
                            null -> UiStrings.rule_clear_custom_settings
                        }
                        if (!mainVm.dialogRequests.confirm(
                            title = UiStrings.action_notice,
                            text = UiStrings.rule_batch_setting_confirmation_prefix(keysToUpdate.size, action) +
                                if (enabled == null) UiStrings.rule_clear_custom_settings_description
                                else UiStrings.rule_custom_settings_description,
                        )) return@runBatchAction
                        toast(vm.applySwitches(request, RuleSetting.from(enabled)).description)
                    }
                }
            }
        }
        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        val itemKeys = remember(groups, appExists, showAppRestriction) { buildList {
            if (showAppRestriction) add("app-restrictions")
            if (appExists) add("app-switch")
            addAll(groups.map { it.key })
        } }
        val focus = rememberRuleListFocus(
            requestKey = focusGroupKey,
            scrollState = pageScrollState,
            itemKeys = itemKeys,
            targetExists = app.groups.any { it.key == focusGroupKey },
        )
        pageScrollState.ResetOnChange(app.groups.isEmpty(), enabled = !focus.pending)
        Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
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
                        subtitle = appId,
                        showApp = true,
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
                                text = UiStrings.action_copy,
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
                    } else {
                    }
                },
            )
        }, floatingActionButton = {
            if (editable) {
                GkAnimatedFloatingActionButton(
                    visible = !isSelectedMode,
                    onClick = {
                        mainVm.navigatePage(
                            UpsertRuleGroupRoute(
                                subsId = subsItemId,
                                groupKey = null,
                                appId = appId
                            )
                        )
                    },
                    contentDescription = UiStrings.rule_add,
                    imageVector = GkIcons.Add,
                )
            }
        }) { contentPadding ->
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
                    if (appExists) {
                        item("app-switch") {
                            GkRuleListItem(onClick = { if (!isSelectedMode) showAppSetting = true }, trailing = {
                                if (!isSelectedMode) GkRuleEnableControl(appControl, setApp, modifier = it,
                                    identity = li.gkd.app.domain.rule.RuleSwitchTarget.App(subsItemId, appId))
                            }) {
                                Text(UiStrings.rule_enable_in_app, style = MaterialTheme.typography.titleSmall)
                                val restriction = RulePropertyText.restrictionSummary(appControl)
                                if (restriction != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(restriction, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(groups, { it.key }) { group ->
                        GkRuleGroupCard(
                            modifier = Modifier.animateListItem(),
                            subs = subs,
                            appId = appId,
                            group = group,
                            control = controls.getValue(group.key),
                            onOpen = {
                                mainVm.showRuleGroup(
                                    subscriptionId = subs.id,
                                    appId = appId,
                                    group = group,
                                )
                            },
                            onSettingChange = { setting ->
                                val request = vm.prepareSwitches(state, setOf(group.key))
                                scope.launchUi { vm.applySwitches(request, setting).failureMessage?.let { toast(it) } }
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
                    item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                        if (groups.isEmpty()) {
                            GkEmptyState(text = if (app.groups.isEmpty()) UiStrings.rules_empty else UiStrings.rules_no_filter_matches)
                        } else {
                            GkPageBottomSpace()
                        }
                    }
                }
            }
        }
    }
}
