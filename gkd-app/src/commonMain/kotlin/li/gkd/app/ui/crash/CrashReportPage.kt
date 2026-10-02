package li.gkd.app.ui.crash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import li.gkd.app.crash.CrashData
import li.gkd.app.resources.Res
import li.gkd.app.resources.crash_report_delete
import li.gkd.app.resources.crash_report_delete_confirmation
import li.gkd.app.resources.crash_reports_clear
import li.gkd.app.resources.crash_reports_clear_confirmation
import li.gkd.app.resources.delete_success
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.page.CrashReportScreen
import li.gkd.app.ui.text.displayMessage
import org.jetbrains.compose.resources.getString

@Composable
fun CrashReportPage(
    vm: CrashReportViewModel,
    dialogs: DialogRequests,
    onBack: () -> Unit,
    onReport: () -> Unit,
    onExport: () -> Unit,
    onCopy: (String) -> Unit,
    toast: (String) -> Unit,
) {
    val records by vm.crashDataState.collectAsStateWithLifecycle()
    var expandedCrashId by rememberSaveable { mutableStateOf(records.value?.firstOrNull()?.id) }
    fun delete(record: CrashData?) {
        vm.scope.launch {
            try {
                if (dialogs.confirm(
                        title = getString(if (record == null) Res.string.crash_reports_clear else Res.string.crash_report_delete),
                        text = getString(if (record == null) Res.string.crash_reports_clear_confirmation else Res.string.crash_report_delete_confirmation),
                        error = true,
                    )
                ) {
                    if (record == null) vm.deleteAllCrashes() else vm.deleteCrash(record)
                    if (record == null || expandedCrashId == record.id) expandedCrashId = null
                    toast(getString(Res.string.delete_success))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.displayMessage())
            }
        }
    }
    CrashReportScreen(
        records,
        expandedCrashId,
        onBack,
        onClear = { delete(null) },
        onDelete = ::delete,
        onToggle = { expandedCrashId = if (expandedCrashId == it) null else it },
        onReport = onReport,
        onExport = onExport,
        onCopy = onCopy,
    )
}
