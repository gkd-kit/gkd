package li.gkd.app.ui.subscription

import li.gkd.app.model.AppInfo
import li.gkd.app.settings.SettingsStore
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.util.SortUtils

fun filterSubsApps(
    apps: List<RawSubscription.RawApp>,
    appMap: Map<String, AppInfo>,
    settings: SettingsStore,
    appActionOrderMap: Map<String, Int>,
    appVisitOrderMap: Map<String, Int>,
    blockSet: Set<String>,
    appGroupType: (SettingsStore) -> Int,
    sortType: (SettingsStore) -> AppSortOption,
    showBlockApps: (SettingsStore) -> Boolean,
): List<RawSubscription.RawApp> {
    var result = apps.sortedWith { a, b ->
        // 默认顺序: 已安装(有名字->无名字)->未安装(有名字(来自订阅)->无名字)
        val x = appMap[a.id]?.name ?: a.name?.let { "\uFFFF" + it }
        ?: ("\uFFFF\uFFFF" + a.id)
        val y = appMap[b.id]?.name ?: b.name?.let { "\uFFFF" + it }
        ?: ("\uFFFF\uFFFF" + b.id)
        SortUtils.collator.compare(x, y)
    }
    result = when (sortType(settings)) {
        AppSortOption.ByActionTime -> {
            result.sortedBy { appActionOrderMap[it.id] ?: Int.MAX_VALUE }
        }

        AppSortOption.ByAppName -> result

        AppSortOption.ByUsedTime -> {
            result.sortedBy { appVisitOrderMap[it.id] ?: Int.MAX_VALUE }
        }
    }
    val groupType = appGroupType(settings)
    result = when {
        groupType == 0 -> emptyList()
        AppGroupOption.allObjects.all { it.include(groupType) } -> result
        else -> result.filter { app ->
            val appInfo = appMap[app.id]
            when {
                appInfo == null -> AppGroupOption.UnInstalledGroup.include(groupType)
                appInfo.isSystem -> AppGroupOption.SystemGroup.include(groupType)
                else -> AppGroupOption.UserGroup.include(groupType)
            }
        }
    }
    if (!showBlockApps(settings)) {
        result = result.filterNot { it.id in blockSet }
    }
    return result
}
