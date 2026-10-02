package li.gkd.app.ui.subscription

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.add_success
import li.gkd.app.resources.unchanged
import li.gkd.app.resources.update_success
import li.gkd.app.subscription.CategoryPolicy
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.page.CategoryEditorScreen
import org.jetbrains.compose.resources.getString

@Composable
fun CategoryEditorPage(
    route: CategoryEditorRoute,
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
) {
    val vm = viewModel { CategoryEditorViewModel(route) }
    val policy = remember { CategoryPolicy() }
    GkSubscriptionPageContent(vm.uiState, onBack) { subscription ->
        CategoryEditorScreen(
            subscription, route.categoryKey, policy,
            frame = { editor, content -> editorFrame(editor, null, content) },
            appName = { app, modifier ->
                GkAppNameText(
                    app.id, app.name, modifier,
                    MaterialTheme.typography.titleSmall, MaterialTheme.colorScheme.primary
                )
            },
        ) { name, description ->
            val changed = vm.save(subscription, name, description)
            showToast(
                getString(if (!changed) Res.string.unchanged
                else if (route.categoryKey == null) Res.string.add_success else Res.string.update_success)
            )
        }
    }
}
