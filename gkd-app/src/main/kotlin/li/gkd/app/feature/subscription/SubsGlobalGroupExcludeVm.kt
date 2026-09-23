package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import li.gkd.app.text.UiStrings
import li.gkd.app.data.ExcludeData
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

data class SubsGlobalGroupExcludeUiState(
    val subscription: RawSubscription,
    val group: RawSubscription.RawGlobalGroup,
    val configs: SubscriptionConfigSnapshot,
    val appActionOrder: Map<String, Int>,
) {
    val excludeData: ExcludeData = ExcludeData.parse(configs.globalGroupConfigs.find {
        it.subsId == subscription.id && it.groupKey == group.key
    }?.exclude)
    val declaredAppIds: Set<String> = (group.apps.orEmpty().map { it.id } +
        group.rules.flatMap { it.apps.orEmpty().map { app -> app.id } } +
        excludeData.appIds.keys + excludeData.activityIds.map { it.first }).toSet()
}

class SubsGlobalGroupExcludeVm(val route: SubsGlobalGroupExcludeRoute) : BaseViewModel() {
    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state
    suspend fun runAction(action: suspend () -> Unit) { mutation.tryWithStateLock(action) }

    val uiState = requiredSubscription(route.subsItemId).buildUiState { subscription ->
        combine(Db.subscriptionConfigStore.observe(), Db.actionLogDao.queryLatestUniqueAppIds(route.subsItemId, route.groupKey)) { configs, ids ->
            SubsGlobalGroupExcludeUiState(subscription,
                subscription.globalGroups.find { it.key == route.groupKey } ?: error(UiStrings.global_rule_missing), configs, ids.mapIndexed { i, id -> id to i }.toMap())
        }
    }

    fun setSortType(option: AppSortOption) {
        AppStore.updateSettings { it.copy(subsExcludeSort = option.value) }
    }
    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(subsExcludeAppGroupType = value) }
    }
    fun toggleShowBlockApps() {
        AppStore.updateSettings { it.copy(subsExcludeShowBlockApp = !it.subsExcludeShowBlockApp) }
    }

    fun prepareSwitches(state: SubsGlobalGroupExcludeUiState, appIds: Set<String>): RuleSwitchRequest =
        RuleGroupConfigService.prepare(appIds.map { RuleSwitchTarget.GlobalApp(route.subsItemId, route.groupKey, it) },
            listOf(state.subscription), state.configs)

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) = RuleGroupConfigService.apply(request, setting)

}
