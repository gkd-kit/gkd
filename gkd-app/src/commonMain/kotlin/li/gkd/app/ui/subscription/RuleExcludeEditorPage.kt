package li.gkd.app.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.Res
import li.gkd.app.resources.config_edit
import li.gkd.app.resources.global_rule_scope_input_hint
import li.gkd.app.resources.page_exclusion
import li.gkd.app.resources.page_exclusion_input_hint
import li.gkd.app.resources.unchanged
import li.gkd.app.resources.update_success
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.navigation.RuleExcludeEditorRoute
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun RuleExcludeEditorPage(
    route: RuleExcludeEditorRoute,
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
) {
    val vm = viewModel { RuleExcludeEditorViewModel(route) }
    GkSubscriptionPageContent(vm.uiState, onBack) { state ->
        val subscription = remember { state.subscription }
        val originalExclude = rememberSaveable { state.exclude.stringify() }
        val expected = remember(originalExclude) { ExcludeData.parse(originalExclude) }
        var text by rememberSaveable { mutableStateOf(expected.stringify(route.appId)) }
        val value = remember(text, expected, route.appId) {
            val appId = route.appId
            if (appId == null) ExcludeData.parse(text) else {
                // Editing one application's pages must preserve unrelated exclusions.
                expected.copy(activityIds = expected.activityIds.filterTo(mutableSetOf()) {
                    it.first != route.appId
                } + ExcludeData.parse(text, appId).activityIds)
            }
        }
        editorFrame(
            EditorSession(
                title = state.group.name,
                titleContent = {
                    GkTwoLineText(
                        state.group.name,
                        if (route.appId == null) stringResource(Res.string.config_edit) else stringResource(
                            Res.string.page_exclusion
                        )
                    )
                },
                hasChanges = { value != expected },
                onSave = {
                    val changed = vm.save(subscription, expected, value)
                    showToast(
                        if (changed) getString(Res.string.update_success) else getString(Res.string.unchanged)
                    )
                },
            ), null
        ) { padding ->
            GkMultiTextField(
                modifier = Modifier.scaffoldPadding(padding),
                text = text,
                onTextChange = { text = it },
                immediateFocus = true,
                placeholderText = if (route.appId == null) stringResource(Res.string.global_rule_scope_input_hint) else stringResource(
                    Res.string.page_exclusion_input_hint
                ),
            )
        }
    }
}
