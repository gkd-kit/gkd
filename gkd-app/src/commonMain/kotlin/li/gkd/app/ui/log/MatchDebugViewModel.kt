package li.gkd.app.ui.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.gkd.app.matching.diagnostics.*
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.MultiSelectionState
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import org.jetbrains.compose.resources.getString

class MatchDebugViewModel : BaseViewModel() {
    val selection = MultiSelectionState<String>()
    val records =
        MatchDiagnosticFiles.changes.map { MatchDiagnosticFiles.records() }.stateLoadable()

    fun delete(records: List<MatchDiagnosticRecord>) {
        scope.launchUi {
            val main = MainViewModel.requireCurrent()
            if (
                !main.dialogRequests.confirm(
                    title = getString(Res.string.action_delete_records),
                    text =
                        getString(Res.string.match_debug_delete_confirm, records.size.toString()),
                    confirmText = getString(Res.string.action_delete),
                    error = true,
                )
            )
                return@launchUi
            MatchDiagnosticFiles.delete(records)
            selection.removeDeleted(records.mapTo(mutableSetOf()) { it.file.name })
            if (selected.value?.key in records) dismiss(checkNotNull(selected.value))
        }
    }

    val selected: StateFlow<SheetRequest<MatchDiagnosticRecord>?>
        field = MutableStateFlow<SheetRequest<MatchDiagnosticRecord>?>(null)

    fun dismiss(request: SheetRequest<MatchDiagnosticRecord>) {
        if (selected.value === request) selected.value = null
    }

    fun show(record: MatchDiagnosticRecord) {
        selected.value = SheetRequest(record)
    }
}
