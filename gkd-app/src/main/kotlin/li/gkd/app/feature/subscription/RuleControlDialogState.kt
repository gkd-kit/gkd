package li.gkd.app.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.share.DeletionTarget

private class RuleControlRequest(val target: RuleGroupTarget.App)

class RuleControlDialogState {
    private val requestFlow = MutableStateFlow<RuleControlRequest?>(null)

    fun show(target: RuleGroupTarget.App) {
        requestFlow.value = RuleControlRequest(target)
    }

    private fun dismiss(request: RuleControlRequest) {
        if (requestFlow.value === request) requestFlow.value = null
    }

    fun dismissForDeletion(targets: Set<DeletionTarget>) {
        val request = requestFlow.value ?: return
        val target = request.target
        if (DeletionTarget.Subscription(target.subsId) in targets ||
            DeletionTarget.App(target.subsId, target.appId) in targets ||
            DeletionTarget.Group(target.subsId, target.appId, target.groupKey) in targets) {
            dismiss(request)
        }
    }

    @Composable
    fun Render() {
        val request by requestFlow.collectAsStateWithLifecycle()
        request?.let { current ->
            key(current) {
                val target = current.target
                val environment = rememberRuleControlEnvironment()
                val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
                val subscription = subscriptions.value?.subscriptions?.get(target.subsId)
                val group = subscription?.apps?.find { it.id == target.appId }?.groups?.find { it.key == target.groupKey }
                val configurationFlow = remember(target) { RuleGroupConfigService.groupConfiguration(target) }
                val configuration by configurationFlow.collectAsStateWithLifecycle(null)
                val missing = subscriptions is Loadable.Ready && (subscription == null || group == null)
                LaunchedEffect(missing) {
                    if (missing) dismiss(current)
                }
                val snapshot = configuration?.snapshot
                if (!missing && subscription != null && group != null && snapshot != null) {
                    GkRuleControlDialog(
                        subscription = subscription,
                        group = group,
                        appId = target.appId,
                        configuration = snapshot,
                        control = environment.resolve(subscription, group, target.appId, snapshot),
                        appEnabled = environment.app(target.subsId, target.appId, snapshot).configuredEnabled,
                        onDismissRequest = { dismiss(current) },
                    )
                }
            }
        }
    }
}
