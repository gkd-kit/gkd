package li.gkd.app.ui.subscription

import li.gkd.app.record.TriggerStats
import li.gkd.app.ui.component.MultiSelectionState
import kotlinx.coroutines.flow.MutableStateFlow
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
import li.gkd.app.app.AppSort
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class SubsAppListUiState(
    val subscription: RawSubscription,
    val configs: SubscriptionConfigSnapshot,
    val appActionOrder: Map<String, Int>,
    val triggerStats: TriggerStats,
)

class SubsAppListViewModel(
    val route: SubsAppListRoute,
) : BaseViewModel() {
    val selection = MultiSelectionState<String>()

    val searchStr: StateFlow<String>
        field = MutableStateFlow("")

    fun setSearchStr(value: String) {
        searchStr.value = value
    }

    val showSearchBar: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowSearchBar(value: Boolean) {
        showSearchBar.value = value
    }

    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state
    suspend fun runAction(action: suspend () -> Unit) {
        mutation.tryWithStateLock(action)
    }

    val uiState = RequiredSubscription(route.subsItemId, scope).buildUiState { subscription ->
        combine(
            SubscriptionConfigStore.observe(),
            Db.actionLogDao.queryLatestUniqueAppIds(route.subsItemId),
            Db.ruleTriggerStatsDao.observe(subsId = route.subsItemId, groupType = li.gkd.db.RuleGroupType.App)
        ) { configs, ids, records ->
            SubsAppListUiState(subscription, configs, ids.mapIndexed { i, id -> id to i }.toMap(), TriggerStats(records))
        }
    }

    fun setSortType(value: AppSort) {
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
