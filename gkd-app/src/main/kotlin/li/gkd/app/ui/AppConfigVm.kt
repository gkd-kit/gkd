package li.gkd.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.ruleconfig.RuleSwitchRequest
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.toSwitchTarget
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.MutexState
import li.gkd.app.util.RuleSortOption
import li.gkd.app.util.toJson5String
import li.gkd.db.ActionLog
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot

data class AppRuleSubscription(val subsItem: SubsItem, val subscription: RawSubscription)

data class AppConfigUiState(
    val configs: SubscriptionConfigSnapshot,
    val subsPairs: List<Pair<AppRuleSubscription, List<RawSubscription.RawGroupProps>>>,
    val latestLogs: List<ActionLog>,
)

class AppConfigVm(
    val route: AppConfigRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    fun removeFromWhitelist() {
        AppStore.updateBlockMatchAppList { it - route.appId }
    }

    fun removeFromPartialDisable() {
        AppStore.updateBlockA11yAppList { it - route.appId }
    }

    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    fun setRuleSortType(option: RuleSortOption) {
        AppStore.updateSettings { it.copy(appRuleSort = option.value) }
    }

    fun setShowDisabledRules(show: Boolean) {
        AppStore.updateSettings { it.copy(showDisabledRule = show) }
    }

    val uiState: StateFlow<Loadable<AppConfigUiState>> = SubscriptionRepository.snapshotFlow.flatMapLatest { source ->
        when (source) {
            Loadable.Loading -> flowOf(Loadable.Loading)
            is Loadable.Failure -> flowOf(source)
            is Loadable.Ready -> combine<SubscriptionConfigSnapshot, List<ActionLog>, Loadable<AppConfigUiState>>(
                Db.subscriptionConfigStore.observe(),
                Db.actionLogDao.queryLatestByAppId(route.appId),
            ) { configs, logs ->
                Loadable.Ready(AppConfigUiState(
                    configs = configs,
                    subsPairs = configs.subsItems.filter { it.enable }.sortedBy { it.order }.mapNotNull { item ->
                        val sub = source.value.subscriptions[item.id] ?: return@mapNotNull null
                        val appEnabled = configs.appConfigs.find {
                            it.subsId == item.id && it.appId == route.appId
                        }?.enable != false
                        // The app gate overrides individual settings, including manually enabled groups.
                        // Global groups have their own per-app switches and remain available here.
                        val groups = sub.globalGroups + if (appEnabled) sub.getAppGroups(route.appId) else emptyList()
                        (AppRuleSubscription(item, sub) to groups).takeIf { groups.isNotEmpty() }
                    },
                    latestLogs = logs,
                ))
            }.catch { emit(Loadable.Failure(it)) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    fun prepareSwitches(state: AppConfigUiState, targets: Set<RuleGroupTarget>): RuleSwitchRequest =
        RuleGroupConfigService.prepare(targets.map { it.toSwitchTarget() },
            state.subsPairs.map { it.first.subscription }, state.configs)

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) = RuleGroupConfigService.apply(request, setting)

    suspend fun buildSelectedGroupsText(selectedGroups: Set<RuleGroupTarget>): String =
        withContext(Dispatchers.Default) {
            val selectedKeys = selectedGroups.mapTo(mutableSetOf()) {
                Triple(it.subsId, it.groupType, it.groupKey)
            }
            val subsPairs = uiState.value.value?.subsPairs.orEmpty()
            val groups = subsPairs.flatMap { (entry, groups) ->
                groups.filterIsInstance<RawSubscription.RawAppGroup>().filter { group ->
                    Triple(entry.subsItem.id, RuleGroupType.App, group.key) in selectedKeys
                }
            }
            check(groups.isNotEmpty()) { UiStrings.selected_app_rules_no_copyable }
            toJson5String(
                RawSubscription.RawApp(
                    id = route.appId,
                    name = AppInfoRepository.appInfoMapFlow.value[route.appId]?.name,
                    groups = groups,
                )
            )
        }

}
