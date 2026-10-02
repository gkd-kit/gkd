package li.gkd.app.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_rule_missing
import li.gkd.app.resources.delete_named_confirmation
import li.gkd.app.resources.delete_success
import li.gkd.app.resources.rule_delete
import li.gkd.app.resources.rule_missing
import li.gkd.app.rule.RuleControlState
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupConfiguration
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.edit
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.ConfirmDeletion
import li.gkd.app.ui.navigation.RuleExcludeEditorRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.text.getSync

private data class RuleSheetSnapshot(
    val target: RuleGroupTarget,
    val subscription: RawSubscription,
    val group: RawSubscription.RawGroupProps,
    val configuration: RuleGroupConfiguration,
    val control: RuleControlState,
)

class RuleGroupState(
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
            (group.appId?.let { DeletionTarget.App(group.subsId, it) in targets } == true)
        ) {
            dismissGroupShow(request)
        }
    }

    private fun openExcludeEditor(
        state: RuleGroupTarget,
        dismiss: () -> Unit,
        onNavigate: (AppRoute) -> Unit,
    ) {
        dismiss()
        onNavigate(
            if (state.appId == null) SubsGlobalGroupExcludeRoute(state.subsId, state.groupKey)
            else RuleExcludeEditorRoute(state.subsId, state.groupKey, state.appId)
        )
    }

    private suspend fun deleteGroup(
        state: RuleGroupTarget,
    ) {
        SubscriptionRepository.update(state.subsId) { subscription ->
            when (state) {
                is RuleGroupTarget.Global -> subscription.edit {
                    if (removeGlobalGroups { it.key == state.groupKey }.isEmpty()) {
                        error(Res.string.rule_missing.getSync())
                    }
                }

                is RuleGroupTarget.App -> subscription.edit {
                    if (subscription.apps.none { it.id == state.appId }) {
                        error(Res.string.app_rule_missing.getSync())
                    }
                    if (removeAppGroups(state.appId) { it.key == state.groupKey }.isEmpty()) {
                        error(Res.string.rule_missing.getSync())
                    }
                }
            }
        }
    }

    @Composable
    fun Render(
        onNavigate: (AppRoute) -> Unit,
        showToast: (String) -> Unit,
        topRoute: () -> NavKey,
        openSubscription: (Long) -> Unit,
        ruleControl: RuleControlDialogState,
        confirmDelete: ConfirmDeletion,
        copyText: (String) -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        val environment = rememberRuleControlEnvironment()
        val requested by showGroupFlow.collectAsStateWithLifecycle()
        val target = requested?.key
        val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
        val subscription = subscriptions.value?.subscriptions?.get(target?.subsId)
        val group =
            if (target?.appId == null) subscription?.globalGroups?.find { it.key == target?.groupKey } else subscription?.apps?.find { it.id == target.appId }?.groups?.find { it.key == target.groupKey }
        val configurationFlow = remember(target) {
            target?.let(RuleGroupConfigService::groupConfiguration) ?: flowOf(null)
        }
        val configuration =
            key(requested) { configurationFlow.collectAsStateWithLifecycle(null).value }
        val snapshot =
            if (target != null && subscription != null && group != null && configuration != null) {
                RuleSheetSnapshot(
                    target, subscription, group, configuration,
                    environment.resolve(
                        subscription,
                        group,
                        target.pageAppId,
                        configuration.snapshot
                    )
                )
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
            GkRuleGroupDialog(
                onNavigate, topRoute, openSubscription, ruleControl, copyText,
                sheetState = sheetState,
                subs = showSubs,
                group = showGroup,
                appId = showGroupState.pageAppId,
                excludeData = excludeData,
                excludeAppId = showGroupState.appId,
                onDismissRequest = dismiss,
                control = displayed.control,
                onSettingChange = { setting ->
                    val change = RuleGroupConfigService.prepare(
                        listOf(showGroupState.toSwitchTarget()),
                        listOf(showSubs),
                        configSnapshot.snapshot
                    )
                    launchUi(scope, showToast) {
                        RuleGroupConfigService.apply(change, setting)
                            .failureMessage()
                            ?.let { showToast(it) }
                    }
                },
                onClickEdit = {
                    dismiss()
                    onNavigate(
                        UpsertRuleGroupRoute(
                            subsId = showGroupState.subsId,
                            groupKey = showGroupState.groupKey,
                            appId = showGroupState.appId,
                        )
                    )
                },
                onClickEditExclude = {
                    openExcludeEditor(showGroupState, dismiss, onNavigate)
                },
                onClickDelete = {
                    confirmDelete(
                        Res.string.rule_delete.getSync(),
                        Res.string.delete_named_confirmation.getSync(showGroup.name),
                        {
                            setOf(
                                DeletionTarget.Group(
                                    showGroupState.subsId,
                                    showGroupState.appId,
                                    showGroupState.groupKey
                                )
                            )
                        },
                        dismiss
                    ) {
                        deleteGroup(showGroupState)
                        showToast(Res.string.delete_success.getSync())
                    }
                }
            )
        }

    }
}
