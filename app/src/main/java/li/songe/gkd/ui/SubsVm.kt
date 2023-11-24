package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.Tuple3
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SubsVm @Inject constructor(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = SubsPageDestination.argsFrom(stateHandle)

    val subsItemFlow =
        subsItemsFlow.map(viewModelScope) { s -> s.find { v -> v.id == args.subsItemId } }

    private val appSubsConfigsFlow = DbSet.subsConfigDao.queryAppTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val groupSubsConfigsFlow = DbSet.subsConfigDao.querySubsGroupTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val appsFlow = combine(
        subsItemFlow, subsIdToRawFlow, appInfoCacheFlow
    ) { subsItem, subsIdToRaw, appInfoCache ->
        val collator = Collator.getInstance(Locale.CHINESE)
        (subsIdToRaw[subsItem?.id]?.apps ?: emptyList()).sortedWith { a, b ->
            // 顺序: 已安装(有名字->无名字)->未安装(有名字(来自订阅)->无名字)
            collator.compare(appInfoCache[a.id]?.name ?: a.name?.let { "\uFFFF" + it }
            ?: ("\uFFFF\uFFFF" + a.id),
                appInfoCache[b.id]?.name ?: b.name?.let { "\uFFFF" + it }
                ?: ("\uFFFF\uFFFF" + b.id))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchStrFlow = MutableStateFlow("")

    private val debounceSearchStr = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)

    private val appAndConfigsFlow = combine(appsFlow,
        appSubsConfigsFlow,
        groupSubsConfigsFlow,
        storeFlow.map(viewModelScope) { s -> s.enableGroup }) { apps, appSubsConfigs, groupSubsConfigs, enableGroup ->
        apps.map { app ->
            val subsConfig = appSubsConfigs.find { s -> s.appId == app.id }
            val appGroupSubsConfigs = groupSubsConfigs.filter { s -> s.appId == app.id }
            val enableSize = app.groups.count { g ->
                appGroupSubsConfigs.find { s -> s.groupKey == g.key }?.enable ?: enableGroup
                ?: g.enable ?: true
            }
            Tuple3(app, subsConfig, enableSize)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filterAppAndConfigsFlow = combine(
        appAndConfigsFlow, debounceSearchStr, appInfoCacheFlow
    ) { appAndConfigs, searchStr, appInfoCache ->
        if (searchStr.isBlank()) {
            appAndConfigs
        } else {
            val results = mutableListOf<Tuple3<SubscriptionRaw.AppRaw, SubsConfig?, Int>>()
            val remnantList = appAndConfigs.toMutableList()
            //1. 搜索已安装应用名称
            remnantList.toList().apply { remnantList.clear() }.forEach { a ->
                val name = appInfoCache[a.t0.id]?.name
                if (name?.contains(searchStr, true) == true) {
                    results.add(a)
                } else {
                    remnantList.add(a)
                }
            }
            //2. 搜索未安装应用名称
            remnantList.toList().apply { remnantList.clear() }.forEach { a ->
                val name = a.t0.name
                if (appInfoCache[a.t0.id] == null && name?.contains(searchStr, true) == true) {
                    results.add(a)
                } else {
                    remnantList.add(a)
                }
            }
            //3. 搜索应用 id
            remnantList.toList().apply { remnantList.clear() }.forEach { a ->
                if (a.t0.id.contains(searchStr, true)) {
                    results.add(a)
                } else {
                    remnantList.add(a)
                }
            }
            results
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}