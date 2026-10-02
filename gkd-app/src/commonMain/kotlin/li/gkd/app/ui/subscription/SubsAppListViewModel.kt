package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class SubsAppListUiState(
    val subscription: RawSubscription,
    val configs: SubscriptionConfigSnapshot,
    val appActionOrder: Map<String, Int>,
)

class SubsAppListViewModel(
    val route: SubsAppListRoute,
) : BaseViewModel() {
    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state
    suspend fun runAction(action: suspend () -> Unit) {
        mutation.tryWithStateLock(action)
    }

    val uiState = RequiredSubscription(route.subsItemId, scope).buildUiState { subscription ->
        combine(
            SubscriptionConfigStore.observe(),
            Db.actionLogDao.queryLatestUniqueAppIds(route.subsItemId)
        ) { configs, ids ->
            SubsAppListUiState(subscription, configs, ids.mapIndexed { i, id -> id to i }.toMap())
        }
    }

    fun setSortType(value: AppSortOption) {
        SettingsRepository.updateSettings { it.copy(subsAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(subsAppGroupType = value) }
    }

    fun toggleShowBlockApps() {
        SettingsRepository.updateSettings { it.copy(subsAppShowBlock = !it.subsAppShowBlock) }
    }

    fun prepareSwitches(state: SubsAppListUiState, appIds: Set<String>): RuleSwitchRequest =
        RuleGroupConfigService.prepare(
            appIds.map { RuleSwitchTarget.App(route.subsItemId, it) },
            listOf(state.subscription), state.configs
        )

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)
}
