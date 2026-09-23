package li.gkd.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.data.CrashData
import li.gkd.app.data.deleteCrashDataList
import li.gkd.app.data.loadCrashDataList
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable

class CrashReportVm(
    initialCrashDataList: List<CrashData>,
) : BaseViewModel() {
    var expandedCrashId by mutableStateOf(initialCrashDataList.firstOrNull()?.id)
        private set

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
            Loadable.Ready(loadCrashDataList())
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
            crashData.delete()
        }
        if (deleted) {
            crashDataState.value = Loadable.Ready(
                crashDataState.value.value.orEmpty().filterNot { it.id == crashData.id },
            )
            if (expandedCrashId == crashData.id) {
                expandedCrashId = null
            }
        } else {
            reloadAfterDeleteFailure()
            error(UiStrings.crash_report_delete_failed)
        }
    }

    suspend fun deleteAllCrashes() {
        initialLoadJob.join()
        val deleted = withContext(Dispatchers.IO) {
            deleteCrashDataList()
        }
        crashDataState.value = Loadable.Ready(
            withContext(Dispatchers.IO) { loadCrashDataList() },
        )
        expandedCrashId = null
        if (!deleted) {
            error(UiStrings.crash_reports_delete_partially_failed)
        }
    }

    private suspend fun reloadAfterDeleteFailure() {
        crashDataState.value = Loadable.Ready(
            withContext(Dispatchers.IO) { loadCrashDataList() },
        )
    }

    fun toggleCrash(crashId: Long) {
        expandedCrashId = if (expandedCrashId == crashId) null else crashId
    }
}
