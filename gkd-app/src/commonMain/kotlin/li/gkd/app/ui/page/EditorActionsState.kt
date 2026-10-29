package li.gkd.app.ui.page

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface EditorDialog {
    data object Discard : EditorDialog

    data class Error(val cause: Exception) : EditorDialog

    data class Rejected(val message: String) : EditorDialog
}

sealed interface EditorSaveResult {
    data object Saved : EditorSaveResult

    data class Rejected(val message: String) : EditorSaveResult
}

class EditorActionsState(private val scope: CoroutineScope) {
    val dialog: StateFlow<EditorDialog?>
        field = MutableStateFlow(null)

    fun dismissDialog() {
        dialog.value = null
    }

    fun requestClose(
        hasChanges: suspend () -> Boolean,
        beforeClose: () -> Unit,
        onClose: () -> Unit,
    ) {
        scope.launch {
            try {
                beforeClose()
                if (hasChanges()) dialog.value = EditorDialog.Discard else onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = EditorDialog.Error(e)
            }
        }
    }

    fun save(
        onSave: suspend () -> EditorSaveResult,
        beforeClose: () -> Unit,
        onSaved: () -> Unit,
    ) {
        scope.launch {
            try {
                when (val result = onSave()) {
                    EditorSaveResult.Saved -> Unit
                    is EditorSaveResult.Rejected -> {
                        dialog.value = EditorDialog.Rejected(result.message)
                        return@launch
                    }
                }
                beforeClose()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = EditorDialog.Error(e)
            }
        }
    }
}
