package li.songe.gkd.ui

import li.songe.gkd.MainViewModel
import li.songe.gkd.ui.share.BaseViewModel

class CrashReportVm : BaseViewModel() {
    val crashDataList = MainViewModel.instance.run {
        val v = tempCrashDataList
        tempCrashDataList = emptyList()
        v
    }
}