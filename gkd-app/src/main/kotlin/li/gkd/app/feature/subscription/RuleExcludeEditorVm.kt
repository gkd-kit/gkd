package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.map
import li.gkd.app.ui.share.EditorSaveSession
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.share.BaseViewModel

data class RuleExcludeEditorUiState(
    val subscription: RawSubscription,
    val group: RawSubscription.RawGroupProps,
    val exclude: ExcludeData,
)

class RuleExcludeEditorVm(private val route: RuleExcludeEditorRoute) : BaseViewModel() {
    private val target = route.appId?.let { RuleGroupTarget.App(route.subsId, it, route.groupKey) }
        ?: RuleGroupTarget.Global(route.subsId, route.groupKey)
    private val saveSession = EditorSaveSession<Boolean>()

    val uiState = requiredSubscription(route.subsId).buildUiState { subscription ->
        val group = (route.appId?.let(subscription::getAppGroups) ?: subscription.globalGroups)
            .find { it.key == route.groupKey } ?: error(UiStrings.rule_missing)
        RuleGroupConfigService.groupConfiguration(target).map { configuration ->
            RuleExcludeEditorUiState(subscription, group, ExcludeData.parse(configuration.group?.exclude))
        }
    }

    suspend fun save(subscription: RawSubscription, expected: ExcludeData, value: ExcludeData): Boolean =
        saveSession.save {
            if (value == expected) return@save false
            RuleGroupConfigService.replaceExclude(target, expected, value, subscription)
            true
        }
}
