package li.gkd.app.ui.subscription

import kotlinx.coroutines.CancellationException
import li.gkd.app.model.ExcludeData
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.MainViewModel
import li.gkd.db.SubscriptionConfigStore

data class RuleExcludeEditorDraft(
    val subscription: RawSubscription,
    val expected: ExcludeData,
    val text: String,
)

suspend fun saveRuleExclusions(
    target: RuleGroupTarget,
    subscription: RawSubscription,
    expected: ExcludeData,
    value: ExcludeData,
): Boolean {
    if (value == expected) return false
    if (target is RuleGroupTarget.Global) {
        val enabledApps = value.appIds.filter { (id, excluded) ->
            !excluded && expected.appIds[id] != false
        }.keys
        if (enabledApps.isNotEmpty()) {
            val request = RuleGroupConfigService.prepare(
                enabledApps.map { RuleSwitchTarget.GlobalApp(target.subsId, target.groupKey, it) },
                listOf(subscription), SubscriptionConfigStore.capture(),
            )
            if (!confirmRuleSwitch(request, RuleSetting.Enabled, MainViewModel.requireCurrent().dialogRequests, RuleSwitchHost.GlobalRuleApps)) {
                // Keep the draft and allow retry; EditorSaveSession must not record a completed save.
                throw CancellationException("Rule enable cancelled")
            }
        }
    }
    RuleGroupConfigService.replaceExclude(target, expected, value, subscription)
    return true
}
