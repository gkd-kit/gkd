package li.gkd.app.ui.home

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import li.gkd.app.service.StatusService
import li.gkd.app.store.actionCountFlow
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.getSubsStatus
import li.gkd.app.util.ruleSummaryFlow

class DashboardVm : BaseViewModel() {

    val subsStatusFlow = combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
        getSubsStatus(ruleSummary, count)
    }.stateInit(getSubsStatus(ruleSummaryFlow.value, actionCountFlow.value))

    fun stopStatusService() {
        StatusService.stop()
        storeFlow.update { it.copy(enableStatusService = false) }
    }
}
