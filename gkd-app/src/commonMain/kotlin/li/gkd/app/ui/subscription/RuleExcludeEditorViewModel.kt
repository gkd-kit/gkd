package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.Res
import li.gkd.app.resources.rule_missing
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.navigation.RuleExcludeEditorRoute
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync

data class RuleExcludeEditorUiState(
    val subscription: RawSubscription,
    val group: RawSubscription.RawGroupProps,
    val exclude: ExcludeData,
)

data class RuleExcludeEditorDraft(
    val subscription: RawSubscription,
    val expected: ExcludeData,
    val text: String,
)

class RuleExcludeEditorViewModel(
    private val route: RuleExcludeEditorRoute,
) : BaseViewModel() {
    val draft: StateFlow<RuleExcludeEditorDraft?>
        field = MutableStateFlow(null)

    fun setText(value: String) {
        draft.update { it?.copy(text = value) }
    }

    private val target = route.appId?.let { RuleGroupTarget.App(route.subsId, it, route.groupKey) }
        ?: RuleGroupTarget.Global(route.subsId, route.groupKey)
    private val saveSession = EditorSaveSession<Boolean>()

    val uiState = RequiredSubscription(route.subsId, scope).buildUiState { subscription ->
        val group = (route.appId?.let(subscription::getAppGroups) ?: subscription.globalGroups)
            .find { it.key == route.groupKey }
            ?: error(Res.string.rule_missing.getSync())
        RuleGroupConfigService.groupConfiguration(target).map { configuration ->
            val exclude = ExcludeData.parse(configuration.group?.exclude)
            if (draft.value == null) {
                draft.value = RuleExcludeEditorDraft(subscription, exclude, exclude.stringify(route.appId))
            }
            RuleExcludeEditorUiState(
                subscription,
                group,
                exclude
            )
        }
    }

    suspend fun save(
        subscription: RawSubscription,
        expected: ExcludeData,
        value: ExcludeData
    ): Boolean =
        saveSession.save {
            if (value == expected) return@save false
            RuleGroupConfigService.replaceExclude(target, expected, value, subscription)
            true
        }
}
