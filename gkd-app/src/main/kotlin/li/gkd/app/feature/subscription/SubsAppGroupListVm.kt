package li.gkd.app.feature.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.edit
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.ruleconfig.RuleSwitchRequest
import li.gkd.app.domain.rule.RuleSwitchTarget
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.toRuleGroupTarget
import li.gkd.app.domain.rule.toSwitchTarget
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.MutexState
import li.gkd.app.util.toJson5String
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot

data class SubsAppGroupListUiState(
    val subscription: RawSubscription,
    val app: RawSubscription.RawApp,
    val configs: SubscriptionConfigSnapshot,
)

class SubsAppGroupListVm(
    val route: SubsAppGroupListRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    fun removeFromWhitelist() {
        li.gkd.app.store.AppStore.updateBlockMatchAppList { it - route.appId }
    }

    fun removeFromPartialDisable() {
        li.gkd.app.store.AppStore.updateBlockA11yAppList { it - route.appId }
    }

    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }


    private val subscription = requiredSubscription(route.subsItemId)

    val uiState = subscription.buildUiState { rawSubscription ->
        Db.subscriptionConfigStore.observe().map { configs ->
            SubsAppGroupListUiState(
                subscription = rawSubscription,
                app = rawSubscription.getApp(route.appId),
                configs = configs,
            )
        }
    }

    suspend fun buildSelectedGroupsText(selectedKeys: Set<Int>): String =
        withContext(Dispatchers.Default) {
            val app = uiState.value.value?.app ?: error(UiStrings.subscription_app_not_loaded)
            val groups = app.groups.filter { it.key in selectedKeys }
            check(groups.isNotEmpty()) { UiStrings.selected_rules_no_copyable }
            toJson5String(
                app.copy(
                    groups = groups,
                ),
            )
        }

    fun prepareAppSwitch(state: SubsAppGroupListUiState): RuleSwitchRequest =
        RuleGroupConfigService.prepare(listOf(RuleSwitchTarget.App(route.subsItemId, route.appId)),
            listOf(state.subscription), state.configs)

    fun prepareSwitches(state: SubsAppGroupListUiState, keys: Set<Int>): RuleSwitchRequest {
        val targets = state.app.groups.filter { it.key in keys }
            .map { it.toRuleGroupTarget(route.subsItemId, route.appId).toSwitchTarget() }
        check(targets.size == keys.size) { UiStrings.selected_rules_reselect }
        return RuleGroupConfigService.prepare(targets, listOf(state.subscription), state.configs)
    }

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) = RuleGroupConfigService.apply(request, setting)

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>): Int {
        check(route.subsItemId < 0) { UiStrings.remote_rule_delete_unsupported }
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
