package li.gkd.app.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import li.gkd.app.text.UiStrings
import li.gkd.app.MainViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.edit
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.ruleconfig.RuleGroupConfiguration
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.RuleControlState
import li.gkd.app.domain.rule.toSwitchTarget
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.component.useSubsGroup

private data class RuleSheetSnapshot(
    val target: RuleGroupTarget,
    val subscription: RawSubscription,
    val group: RawSubscription.RawGroupProps,
    val configuration: RuleGroupConfiguration,
    val control: RuleControlState,
)

class RuleGroupState(
    private val mainVm: MainViewModel,
) {
    private val showGroupFlow = MutableStateFlow<SheetRequest<RuleGroupTarget>?>(null)
    private fun dismissGroupShow(request: SheetRequest<RuleGroupTarget>) {
        if (showGroupFlow.value === request) showGroupFlow.value = null
    }

    fun showGroup(state: RuleGroupTarget) {
        showGroupFlow.value = SheetRequest(state)
    }

    fun dismissForDeletion(targets: Set<DeletionTarget>) {
        val request = showGroupFlow.value ?: return
        val group = request.key
        if (DeletionTarget.Subscription(group.subsId) in targets ||
            DeletionTarget.Group(group.subsId, group.appId, group.groupKey) in targets ||
            (group.appId?.let { DeletionTarget.App(group.subsId, it) in targets } == true)) {
            dismissGroupShow(request)
        }
    }

    private fun openExcludeEditor(state: RuleGroupTarget, dismiss: () -> Unit) {
        dismiss()
        mainVm.navigatePage(
            if (state.appId == null) SubsGlobalGroupExcludeRoute(state.subsId, state.groupKey)
            else RuleExcludeEditorRoute(state.subsId, state.groupKey, state.appId),
        )
    }

    private suspend fun deleteGroup(
        state: RuleGroupTarget,
    ) {
        SubscriptionRepository.update(state.subsId) { subscription ->
            when (state) {
                is RuleGroupTarget.Global -> subscription.edit {
                    if (removeGlobalGroups { it.key == state.groupKey }.isEmpty()) {
                        error(UiStrings.rule_missing)
                    }
                }

                is RuleGroupTarget.App -> subscription.edit {
                    if (subscription.apps.none { it.id == state.appId }) {
                        error(UiStrings.app_rule_missing)
                    }
                    if (removeAppGroups(state.appId) { it.key == state.groupKey }.isEmpty()) {
                        error(UiStrings.rule_missing)
                    }
                }
            }
        }
    }

    @Composable
    fun Render() {
        val environment = rememberRuleControlEnvironment()
        val requested by showGroupFlow.collectAsStateWithLifecycle()
        val target = requested?.key
        val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
        val subscription = subscriptions.value?.subscriptions?.get(target?.subsId)
        val group = useSubsGroup(subscription, target?.groupKey, target?.appId)
        val configurationFlow = remember(target) {
            target?.let(RuleGroupConfigService::groupConfiguration) ?: flowOf(null)
        }
        val configuration = key(requested) { configurationFlow.collectAsStateWithLifecycle(null).value }
        val snapshot = if (target != null && subscription != null && group != null && configuration != null) {
            RuleSheetSnapshot(target, subscription, group, configuration,
                environment.resolve(subscription, group, target.pageAppId, configuration.snapshot))
        } else null
        GkRetainedSheet(
            request = requested,
            snapshot = snapshot,
            missing = target != null && subscriptions is Loadable.Ready && (subscription == null || group == null),
            onDismissRequest = ::dismissGroupShow,
        ) { _, displayed, sheetState, dismiss ->
            val showGroupState = displayed.target
            val showSubs = displayed.subscription
            val showGroup = displayed.group
            val configSnapshot = displayed.configuration
            val subsConfig = configSnapshot.group
            val excludeData = remember(subsConfig?.exclude) {
                ExcludeData.parse(subsConfig?.exclude)
            }
            RuleGroupDialog(
                sheetState = sheetState,
                subs = showSubs,
                group = showGroup,
                appId = showGroupState.pageAppId,
                excludeData = excludeData,
                excludeAppId = showGroupState.appId,
                onDismissRequest = dismiss,
                control = displayed.control,
                onSettingChange = { setting ->
                    val change = RuleGroupConfigService.prepare(listOf(showGroupState.toSwitchTarget()), listOf(showSubs), configSnapshot.snapshot)
                    mainVm.scope.launchUi { RuleGroupConfigService.apply(change, setting).failureMessage?.let { toast(it) } }
                },
                onClickEdit = {
                    dismiss()
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = showGroupState.subsId,
                            groupKey = showGroupState.groupKey,
                            appId = showGroupState.appId,
                        )
                    )
                },
                onClickEditExclude = {
                    openExcludeEditor(showGroupState, dismiss)
                },
                onClickDelete = {
                    mainVm.confirmDelete(
                        title = UiStrings.rule_delete,
                        text = UiStrings.delete_named_confirmation(showGroup.name),
                        targets = { setOf(DeletionTarget.Group(showGroupState.subsId, showGroupState.appId, showGroupState.groupKey)) },
                        dismiss = dismiss,
                    ) {
                        deleteGroup(showGroupState)
                        toast(UiStrings.delete_success)
                    }
                }
            )
        }

    }
}
