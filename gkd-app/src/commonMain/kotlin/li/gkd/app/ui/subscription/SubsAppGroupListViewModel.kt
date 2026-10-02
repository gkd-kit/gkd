package li.gkd.app.ui.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.Res
import li.gkd.app.resources.remote_rule_delete_unsupported
import li.gkd.app.resources.selected_rules_no_copyable
import li.gkd.app.resources.selected_rules_reselect
import li.gkd.app.resources.subscription_app_not_loaded
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson.toJson5String
import li.gkd.app.subscription.edit
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class SubsAppGroupListUiState(
    val subscription: RawSubscription,
    val app: RawSubscription.RawApp,
    val configs: SubscriptionConfigSnapshot,
)

class SubsAppGroupListViewModel(
    val route: SubsAppGroupListRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    fun removeFromWhitelist() {
        SettingsRepository.updateBlockMatchAppList { it - route.appId }
    }

    fun removeFromPartialDisable() {
        SettingsRepository.updateBlockA11yAppList { it - route.appId }
    }

    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    private val subscription = RequiredSubscription(route.subsItemId, scope)

    val uiState = subscription.buildUiState { rawSubscription ->
        SubscriptionConfigStore.observe().map { configs ->
            SubsAppGroupListUiState(
                subscription = rawSubscription,
                app = rawSubscription.getApp(
                    route.appId,
                    AppInfoRepository.snapshot?.apps.orEmpty()[route.appId]?.name
                ),
                configs = configs,
            )
        }
    }

    suspend fun buildSelectedGroupsText(selectedKeys: Set<Int>): String =
        withContext(Dispatchers.Default) {
            val app = uiState.value.value?.app
                ?: error(Res.string.subscription_app_not_loaded.getSync())
            val groups = app.groups.filter { it.key in selectedKeys }
            check(groups.isNotEmpty()) { Res.string.selected_rules_no_copyable.getSync() }
            toJson5String(
                app.copy(
                    groups = groups,
                ),
            )
        }

    fun prepareAppSwitch(state: SubsAppGroupListUiState): RuleSwitchRequest =
        RuleGroupConfigService.prepare(
            listOf(RuleSwitchTarget.App(route.subsItemId, route.appId)),
            listOf(state.subscription), state.configs
        )

    fun prepareSwitches(state: SubsAppGroupListUiState, keys: Set<Int>): RuleSwitchRequest {
        val targets = state.app.groups.filter { it.key in keys }
            .map { it.toRuleGroupTarget(route.subsItemId, route.appId).toSwitchTarget() }
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
                deletedSize = removeAppGroups(
                    appId = route.appId,
                    removeAppIfEmpty = true,
                ) { it.key in selectedKeys }.size
            }
        }
        return deletedSize
    }
}
