package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.Res
import li.gkd.app.resources.global_rule_missing
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.navigation.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

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

class SubsGlobalGroupExcludeViewModel(
    val route: SubsGlobalGroupExcludeRoute,
) : BaseViewModel() {
    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state
    suspend fun runAction(action: suspend () -> Unit) {
        mutation.tryWithStateLock(action)
    }

    val uiState = RequiredSubscription(route.subsItemId, scope).buildUiState { subscription ->
        combine(
            SubscriptionConfigStore.observe(),
            Db.actionLogDao.queryLatestUniqueAppIds(route.subsItemId, route.groupKey)
        ) { configs, ids ->
            SubsGlobalGroupExcludeUiState(
                subscription,
                subscription.globalGroups.find { it.key == route.groupKey }
                    ?: error(Res.string.global_rule_missing.getSync()),
                configs,
                ids.mapIndexed { i, id -> id to i }.toMap()
            )
        }
    }

    fun setSortType(option: AppSortOption) {
        SettingsRepository.updateSettings { it.copy(subsExcludeSort = option.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(subsExcludeAppGroupType = value) }
    }

    fun toggleShowBlockApps() {
        SettingsRepository.updateSettings { it.copy(subsExcludeShowBlockApp = !it.subsExcludeShowBlockApp) }
    }

    fun prepareSwitches(
        state: SubsGlobalGroupExcludeUiState,
        appIds: Set<String>
    ): RuleSwitchRequest =
        RuleGroupConfigService.prepare(
            appIds.map { RuleSwitchTarget.GlobalApp(route.subsItemId, route.groupKey, it) },
            listOf(state.subscription), state.configs
        )

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)

}
