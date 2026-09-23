package li.gkd.app.ui.component

import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import li.gkd.app.MainActivity
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.share.launchUiAction

@Composable
fun GkEditorScaffold(
    title: @Composable () -> Unit,
    hasChanges: suspend () -> Boolean,
    onSave: suspend () -> Unit,
    saveEnabled: Boolean = true,
    onSaved: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val mainVm = MainViewModel.requireCurrent()
    val activity = LocalActivity.current as MainActivity
    val entryRoute = remember { mainVm.topRoute }
    val onClose = mainVm.scope.launchUiAction {
        activity.imeController.requestHide()
        if (hasChanges() && !mainVm.dialogRequests.confirm(
                title = UiStrings.notice_title,
                text = UiStrings.edit_discard_confirmation,
            )) return@launchUiAction
        if (mainVm.topRoute === entryRoute) mainVm.popPage()
    }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            GkTopAppBar(
                title = title,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.Close,
                        contentDescription = UiStrings.dialog_close,
                        onClick = onClose,
                    )
                },
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Check,
                        contentDescription = UiStrings.action_save,
                        enabled = saveEnabled,
                        onClick = mainVm.scope.launchUiAction {
                            onSave()
                            if (mainVm.topRoute === entryRoute) {
                                activity.imeController.requestHide()
                                if (onSaved != null) onSaved() else mainVm.popPage()
                            }
                        },
                    )
                },
            )
        },
        content = content,
    )
}
