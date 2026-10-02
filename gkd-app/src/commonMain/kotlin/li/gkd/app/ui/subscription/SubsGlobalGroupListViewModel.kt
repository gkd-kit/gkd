package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.gkd.app.resources.Res
import li.gkd.app.resources.remote_rule_delete_unsupported
import li.gkd.app.resources.selected_rules_reselect
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.edit
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class SubsGlobalGroupListUiState(
    val subscription: RawSubscription,
    val configs: SubscriptionConfigSnapshot,
)

class SubsGlobalGroupListViewModel(
    val route: SubsGlobalGroupListRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    private val subscription = RequiredSubscription(route.subsItemId, scope)

    val uiState = subscription.buildUiState { rawSubscription ->
        SubscriptionConfigStore.observe().map { configs ->
            SubsGlobalGroupListUiState(
                subscription = rawSubscription,
                configs = configs,
            )
        }
    }

    fun prepareSwitches(state: SubsGlobalGroupListUiState, keys: Set<Int>): RuleSwitchRequest {
        val targets = state.subscription.globalGroups.filter { it.key in keys }
            .map { it.toRuleGroupTarget(route.subsItemId).toSwitchTarget() }
        check(targets.size == keys.size) { Res.string.selected_rules_reselect.getSync() }
        return RuleGroupConfigService.prepare(targets, listOf(state.subscription), state.configs)
    }

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>): Int {
        check(route.subsItemId < 0) { Res.string.remote_rule_delete_unsupported.getSync() }
        var deletedSize = 0
        subscription.update { current ->
            current.edit {
                deletedSize = removeGlobalGroups { it.key in selectedKeys }.size
            }
        }
        return deletedSize
    }
}
