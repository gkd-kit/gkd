package li.gkd.app.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.navigation.inputInsets
import li.gkd.app.ui.page.UpsertRuleGroupScreen

@Composable
fun UpsertRuleGroupPage(
    route: UpsertRuleGroupRoute,
    onBack: () -> Unit,
    replaceRoute: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
) {
    val vm = viewModel {
        UpsertRuleGroupViewModel(route, showToast)
    }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var addedAppId by remember { mutableStateOf<String?>(null) }
    GkSubscriptionPageContent(vm.uiState, onBack) { data ->
        val text = draft ?: data.initialText
        UpsertRuleGroupScreen(
            vm.isEdit, vm.isApp, text, { vm.beginEditing(); draft = it },
            { vm.hasTextChanged(text) }, { addedAppId = vm.saveRule(text) },
            inputInsets = inputInsets(),
            frame = { editor, content ->
                editorFrame(editor, {
                    if (route.forward) {
                        replaceRoute(
                            if (route.appId == null) SubsGlobalGroupListRoute(route.subsId)
                            else SubsAppGroupListRoute(route.subsId, addedAppId ?: route.appId)
                        )
                    } else onBack()
                }, content)
            },
        )
    }
}
