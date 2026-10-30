package li.gkd.app.ui.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.matching.diagnostics.*
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.MultiSelectionState
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db
import li.gkd.db.MatchDiagnosticRecord
import org.jetbrains.compose.resources.getString

class MatchDebugViewModel : BaseViewModel() {
    fun showDiagnosticHelp() {
        scope.launchUi {
            MainViewModel.requireCurrent()
                .dialogRequests
                .showMessage(
                    title = getString(Res.string.match_debug_help_title),
                    text = getString(Res.string.match_debug_help_description),
                    confirmText = getString(Res.string.action_got_it),
                )
        }
    }

    fun requestDiagnosticCapture() {
        scope.launchUi {
            val confirmed =
                MainViewModel.requireCurrent()
                    .dialogRequests
                    .confirm(
                        title = getString(Res.string.match_debug_start_title),
                        text = getString(Res.string.match_debug_start_description),
                        confirmText = getString(Res.string.match_debug_start_confirm),
                        dismissOnRequest = true,
                    )
            if (confirmed) startDiagnosticCapture()
        }
    }

    val selection = MultiSelectionState<Long>()
    val records = Db.matchDiagnosticRecordDao.observeAll().stateLoadable()

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
            selection.removeDeleted(records.mapTo(mutableSetOf()) { it.id })
            if (records.any { it.id == selected.value?.key?.id })
                dismiss(checkNotNull(selected.value))
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
