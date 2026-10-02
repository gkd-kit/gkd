package li.gkd.app.app

import li.gkd.app.model.AppInfo

/** Input order is the platform's locale-aware name order; unknown history entries preserve it. */
object AppQuery {
    data class Result(val apps: List<AppInfo>, val showAllApps: Boolean)

    fun select(
        visibleApps: List<AppInfo>, search: String, group: Int, sort: Int,
        showBlocked: Boolean = true, blocked: Set<String> = emptySet(),
        actionOrder: List<String> = emptyList(), visitOrder: Map<String, Int> = emptyMap(),
    ): Result {
        var apps = visibleApps.filter { app ->
            (showBlocked || app.id !in blocked) &&
                    (group and (if (app.isSystem) AppGroupFlags.System else AppGroupFlags.User) != 0)
        }
        val showAll = apps.size == visibleApps.size
        val actionPositions = actionOrder.withIndex().associate { it.value to it.index }
        apps = when (sort) {
            AppSort.ByActionTime.value -> apps.sortedBy { actionPositions[it.id] ?: Int.MAX_VALUE }
            AppSort.ByUsedTime.value -> apps.sortedBy { visitOrder[it.id] ?: Int.MAX_VALUE }
            else -> apps
        }
        if (search.isNotBlank()) {
            apps = (apps.filter { it.name.contains(search, true) } +
                    apps.filter { it.id.contains(search, true) }).distinct()
        }
        return Result(apps, showAll)
    }
}
