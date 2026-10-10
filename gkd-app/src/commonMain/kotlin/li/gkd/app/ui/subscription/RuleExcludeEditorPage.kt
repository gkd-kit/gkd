package li.gkd.app.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.navigation.GkEditor
import li.gkd.app.ui.navigation.RuleExcludeEditorRoute
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.platform.hideIme
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun RuleExcludeEditorPage(
    host: UiHost,
    route: RuleExcludeEditorRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { RuleExcludeEditorViewModel(route) }
    GkSubscriptionPageContent(vm.uiState, mainVm.navigator::pop) { state ->
        val draftState by vm.draft.collectAsStateWithLifecycle()
        val draft = draftState ?: return@GkSubscriptionPageContent
        val subscription = draft.subscription
        val expected = draft.expected
        val text = draft.text
        val value = remember(text, expected, route.appId) {
            val appId = route.appId
            if (appId == null) ExcludeData.parse(text) else {
                // Editing one application's pages must preserve unrelated exclusions.
                expected.copy(activityIds = expected.activityIds.filterTo(mutableSetOf()) {
                    it.first != route.appId
                } + ExcludeData.parse(text, appId).activityIds)
            }
        }
        GkEditor(
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
                    ToastUtils.show(
                        if (changed) getString(Res.string.update_success) else getString(Res.string.unchanged)
                    )
                },
            ), null,
            navigator = mainVm.navigator,
            hideIme = host::hideIme,
            scope = mainVm.scope,
        ) { padding ->
            GkMultiTextField(
                modifier = Modifier.scaffoldPadding(padding),
                text = text,
                onTextChange = vm::setText,
                immediateFocus = true,
                placeholderText = if (route.appId == null) stringResource(Res.string.global_rule_scope_input_hint) else stringResource(
                    Res.string.page_exclusion_input_hint
                ),
            )
        }
    }
}
