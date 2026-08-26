package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import li.songe.gkd.service.StatusService
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow

class DashboardVm : BaseViewModel() {

    val subsStatusFlow = combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
        getSubsStatus(ruleSummary, count)
    }.stateInit(getSubsStatus(ruleSummaryFlow.value, actionCountFlow.value))

    fun stopStatusService() {
        StatusService.stop()
        storeFlow.update { it.copy(enableStatusService = false) }
    }
}
