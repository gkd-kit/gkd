package li.gkd.app.data.subscription

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.a11y.RuleExecutionHost
import li.gkd.app.appScope
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.SubscriptionRepository

object SubscriptionState {
    val subsMapFlow by lazy {
        SubscriptionRepository.snapshotFlow.map { it.value?.subscriptions.orEmpty() }
            .stateIn(
                appScope,
                SharingStarted.Eagerly,
                SubscriptionRepository.snapshotFlow.value.value?.subscriptions.orEmpty(),
            )
    }

    val ruleSummaryFlow by lazy {
        ruleGroupState.map { state ->
            when (state) {
                Loadable.Loading -> Loadable.Loading
                is Loadable.Failure -> state
                is Loadable.Ready -> Loadable.Ready(
                    RuleSummaryBuilder.build(state.value.groups, state.value.apps, RuleExecutionHost.runtime)
                )
            }
        }.catch { emit(Loadable.Failure(it)) }
            .stateIn(appScope, SharingStarted.Eagerly, Loadable.Loading)
    }
}
