package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.ruleconfig.RuleSwitchRequest
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.RuleSwitchTarget
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.MutexState
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot

data class SubsAppListUiState(
    val subscription: RawSubscription,
    val configs: SubscriptionConfigSnapshot,
    val appActionOrder: Map<String, Int>,
)

class SubsAppListVm(val route: SubsAppListRoute) : BaseViewModel() {
    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state
    suspend fun runAction(action: suspend () -> Unit) { mutation.tryWithStateLock(action) }

    val uiState = requiredSubscription(route.subsItemId).buildUiState { subscription ->
        combine(Db.subscriptionConfigStore.observe(), Db.actionLogDao.queryLatestUniqueAppIds(route.subsItemId)) { configs, ids ->
            SubsAppListUiState(subscription, configs, ids.mapIndexed { i, id -> id to i }.toMap())
        }
    }

    fun setSortType(value: AppSortOption) {
        AppStore.updateSettings { it.copy(subsAppSort = value.value) }
    }
    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(subsAppGroupType = value) }
    }
    fun toggleShowBlockApps() {
        AppStore.updateSettings { it.copy(subsAppShowBlock = !it.subsAppShowBlock) }
    }
    fun prepareSwitches(state: SubsAppListUiState, appIds: Set<String>): RuleSwitchRequest =
        RuleGroupConfigService.prepare(appIds.map { RuleSwitchTarget.App(route.subsItemId, it) },
            listOf(state.subscription), state.configs)

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) = RuleGroupConfigService.apply(request, setting)
}
