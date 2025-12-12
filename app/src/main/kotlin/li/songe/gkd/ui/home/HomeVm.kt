package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.songe.gkd.MainViewModel
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.asMutableStateFlow
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.EMPTY_RULE_TIP
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.usedSubsEntriesFlow

class HomeVm : BaseViewModel() {

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateInit(EMPTY_RULE_TIP)
    }

    val usedSubsItemCountFlow = usedSubsEntriesFlow.mapNew { it.size }

    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.appSort) },
        setter = {
            storeFlow.value.copy(appSort = it.value)
        }
    )
    val showBlockAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.showBlockApp },
        setter = {
            storeFlow.value.copy(showBlockApp = it)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.appGroupType },
        setter = {
            storeFlow.value.copy(appGroupType = it)
        }
    )

    val editWhiteListModeFlow = MutableStateFlow(false)
    val blockAppListFlow = MutableStateFlow(blockMatchAppListFlow.value).also { stateFlow ->
        combine(blockMatchAppListFlow, editWhiteListModeFlow) { it }.launchCollect {
            if (!editWhiteListModeFlow.value) {
                stateFlow.value = blockMatchAppListFlow.value
            }
        }
    }

    val appFilter = useAppFilter(
        appGroupTypeFlow = appGroupTypeFlow,
        sortTypeFlow = sortTypeFlow,
        showBlockAppFlow = showBlockAppFlow,
        blockAppListFlow = blockAppListFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow = MutableStateFlow(false).apply {
        launchCollect {
            if (!it) {
                searchStrFlow.value = ""
            }
        }
    }
    val appInfosFlow = appFilter.appListFlow.apply {
        launchOnChange {
            MainViewModel.instance.appListKeyFlow.value++
        }
    }

    val showToastInputDlgFlow = MutableStateFlow(false)
    val showNotifTextInputDlgFlow = MutableStateFlow(false)
    val showToastSettingsDlgFlow = MutableStateFlow(false)
    val showA11yBlockDlgFlow = MutableStateFlow(false)
}
