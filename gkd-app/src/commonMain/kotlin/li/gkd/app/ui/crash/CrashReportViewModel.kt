package li.gkd.app.ui.crash

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.crash.CrashData
import li.gkd.app.crash.FileCrashStorage
import li.gkd.app.resources.Res
import li.gkd.app.resources.crash_report_delete_failed
import li.gkd.app.resources.crash_reports_delete_partially_failed
import li.gkd.app.state.Loadable
import li.gkd.app.ui.state.BaseViewModel
import org.jetbrains.compose.resources.getString

class CrashReportViewModel(
    initialCrashDataList: List<CrashData>,
) : BaseViewModel() {
    val crashDataState: StateFlow<Loadable<List<CrashData>>>
        field = MutableStateFlow(
            if (initialCrashDataList.isEmpty()) {
                Loadable.Loading
            } else {
                Loadable.Ready(initialCrashDataList)
            },
        )

    private val initialLoadJob = scope.launch(Dispatchers.IO) {
        crashDataState.value = try {
            Loadable.Ready(FileCrashStorage.load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (initialCrashDataList.isEmpty()) {
                Loadable.Failure(e)
            } else {
                Loadable.Ready(initialCrashDataList)
            }
        }
    }

    suspend fun deleteCrash(crashData: CrashData) {
        initialLoadJob.join()
        val deleted = withContext(Dispatchers.IO) {
            FileCrashStorage.delete(crashData)
        }
        if (deleted) {
            crashDataState.value = Loadable.Ready(
                crashDataState.value.value.orEmpty().filterNot { it.id == crashData.id },
            )
        } else {
            reloadAfterDeleteFailure()
            error(getString(Res.string.crash_report_delete_failed))
        }
    }

    suspend fun deleteAllCrashes() {
        initialLoadJob.join()
        val deleted = withContext(Dispatchers.IO) {
            FileCrashStorage.deleteAll()
        }
        crashDataState.value = Loadable.Ready(
            withContext(Dispatchers.IO) { FileCrashStorage.load() },
        )
        if (!deleted) {
            error(getString(Res.string.crash_reports_delete_partially_failed))
        }
    }

    private suspend fun reloadAfterDeleteFailure() {
        crashDataState.value = Loadable.Ready(
            withContext(Dispatchers.IO) { FileCrashStorage.load() },
        )
    }

}
