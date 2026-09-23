package li.gkd.app.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.data.ExcludeData
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkEditorScaffold
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.toast

@Serializable
data class RuleExcludeEditorRoute(
    val subsId: Long,
    val groupKey: Int,
    val appId: String? = null,
) : NavKey

@Composable
fun RuleExcludeEditorPage(route: RuleExcludeEditorRoute) {
    val vm = viewModel { RuleExcludeEditorVm(route) }
    GkSubscriptionPageContent(vm.uiState) { state ->
        val subscription = remember { state.subscription }
        val originalExclude = rememberSaveable { state.exclude.stringify() }
        val expected = remember(originalExclude) { ExcludeData.parse(originalExclude) }
        var text by rememberSaveable { mutableStateOf(expected.stringify(route.appId)) }
        val value = remember(text, expected, route.appId) {
            if (route.appId == null) ExcludeData.parse(text) else {
                // Editing one application's pages must preserve unrelated exclusions.
                expected.copy(activityIds = expected.activityIds.filterTo(mutableSetOf()) {
                    it.first != route.appId
                } + ExcludeData.parse(text, route.appId).activityIds)
            }
        }
        GkEditorScaffold(
            title = {
                GkTwoLineText(state.group.name, if (route.appId == null) UiStrings.config_edit else UiStrings.page_exclusion)
            },
            hasChanges = { value != expected },
            onSave = {
                val changed = vm.save(subscription, expected, value)
                toast(if (changed) UiStrings.update_success else UiStrings.unchanged)
            },
        ) { padding ->
            GkMultiTextField(
                modifier = Modifier.scaffoldPadding(padding),
                text = text,
                onTextChange = { text = it },
                immediateFocus = true,
                placeholderText = if (route.appId == null) UiStrings.global_rule_scope_input_hint else UiStrings.page_exclusion_input_hint,
            )
        }
    }
}
