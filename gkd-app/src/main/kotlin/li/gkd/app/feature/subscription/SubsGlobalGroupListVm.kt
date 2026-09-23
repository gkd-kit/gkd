package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.gkd.app.text.UiStrings
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.edit
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.ruleconfig.RuleSwitchRequest
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.toRuleGroupTarget
import li.gkd.app.domain.rule.toSwitchTarget
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.MutexState
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot

data class SubsGlobalGroupListUiState(
    val subscription: RawSubscription,
    val configs: SubscriptionConfigSnapshot,
)

class SubsGlobalGroupListVm(
    val route: SubsGlobalGroupListRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    private val subscription = requiredSubscription(route.subsItemId)

    val uiState = subscription.buildUiState { rawSubscription ->
        Db.subscriptionConfigStore.observe().map { configs ->
            SubsGlobalGroupListUiState(
                subscription = rawSubscription,
                configs = configs,
            )
        }
    }

    fun prepareSwitches(state: SubsGlobalGroupListUiState, keys: Set<Int>): RuleSwitchRequest {
        val targets = state.subscription.globalGroups.filter { it.key in keys }
            .map { it.toRuleGroupTarget(route.subsItemId).toSwitchTarget() }
        check(targets.size == keys.size) { UiStrings.selected_rules_reselect }
        return RuleGroupConfigService.prepare(targets, listOf(state.subscription), state.configs)
    }

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) = RuleGroupConfigService.apply(request, setting)

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>): Int {
        check(route.subsItemId < 0) { UiStrings.remote_rule_delete_unsupported }
        var deletedSize = 0
        subscription.update { current ->
            current.edit {
                deletedSize = removeGlobalGroups { it.key in selectedKeys }.size
            }
        }
        return deletedSize
    }
}
