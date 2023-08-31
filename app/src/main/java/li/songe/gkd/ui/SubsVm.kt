package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.subsIdToRawFlow
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SubsVm @Inject constructor(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = SubsPageDestination.argsFrom(stateHandle)

    val subsItemFlow = DbSet.subsItemDao.queryById(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val subsConfigsFlow = DbSet.subsConfigDao.queryAppTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appAndConfigsFlow = combine(
        subsItemFlow,
        subsConfigsFlow,
        appInfoCacheFlow
    ) { subsItem, subsConfigs, appInfoCache ->
        if (subsItem == null) return@combine emptyList()
        val apps = (subsIdToRawFlow.value[subsItem.id]?.apps ?: emptyList()).sortedWith { a, b ->
            Collator.getInstance(Locale.CHINESE)
                .compare(appInfoCache[a.id]?.name ?: a.id, appInfoCache[b.id]?.name ?: b.id)
        }
        apps.map { app ->
            val subsConfig = subsConfigs.find { s -> s.appId == app.id }
            app to subsConfig
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


}