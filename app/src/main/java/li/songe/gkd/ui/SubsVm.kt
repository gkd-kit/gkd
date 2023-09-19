package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.Tuple3
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

    private val appSubsConfigsFlow = DbSet.subsConfigDao.queryAppTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val groupSubsConfigsFlow = DbSet.subsConfigDao.querySubsGroupTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appAndConfigsFlow = combine(
        subsItemFlow, appSubsConfigsFlow, appInfoCacheFlow, groupSubsConfigsFlow, subsIdToRawFlow
    ) { subsItem, appSubsConfigs, appInfoCache, groupSubsConfigs, subsIdToRaw ->
        if (subsItem == null) return@combine emptyList()
        val apps = (subsIdToRaw[subsItem.id]?.apps ?: emptyList()).sortedWith { a, b ->
            // 顺序: 已安装(有名字->无名字)->未安装(有名字(来自订阅)->无名字)
            Collator.getInstance(Locale.CHINESE)
                .compare(appInfoCache[a.id]?.name ?: a.name?.let { "\uFFFF" + it }
                ?: ("\uFFFF\uFFFF" + a.id),
                    appInfoCache[b.id]?.name ?: b.name?.let { "\uFFFF" + it }
                    ?: ("\uFFFF\uFFFF" + b.id))
        }
        apps.map { app ->
            val subsConfig = appSubsConfigs.find { s -> s.appId == app.id }
            val appGroupSubsConfigs = groupSubsConfigs.filter { s -> s.appId == app.id }
            val enableSize = app.groups.count { g ->
                appGroupSubsConfigs.find { s -> s.groupKey == g.key }?.enable ?: g.enable ?: true
            }
            Tuple3(app, subsConfig, enableSize)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


}