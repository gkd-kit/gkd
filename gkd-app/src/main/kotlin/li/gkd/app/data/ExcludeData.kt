package li.gkd.app.data

import li.gkd.app.util.isValidActivityId
import li.gkd.app.util.isValidAppId

data class ExcludeData(
    val appIds: Map<String, Boolean>,
    val activityIds: Set<Pair<String, String>>,
) {
    val excludeAppIds = appIds.entries.filter { entry -> entry.value }.map { entry -> entry.key }.toHashSet()
    val includeAppIds = appIds.entries.filter { entry -> !entry.value }.map { entry -> entry.key }.toHashSet()

    fun stringify(appId: String? = null): String {
        return if (appId != null) {
            activityIds.filter { entry -> entry.first == appId }.map { entry -> entry.second }.sorted()
                .joinToString("\n\n")
        } else {
            (appIds.entries.map { entry ->
                if (entry.value) {
                    entry.key
                } else {
                    "!${entry.key}"
                }
            } + activityIds.map { entry -> "${entry.first}/${entry.second}" })
                .sorted()
                .joinToString("\n\n")
        }
    }

    fun clear(appId: String): ExcludeData {
        return copy(
            appIds = appIds.toMutableMap().apply {
                remove(appId)
            },
        )
    }

    fun switch(appId: String, activityId: String? = null): ExcludeData {
        return if (activityId == null) {
            copy(
                appIds = appIds.toMutableMap().apply {
                    if (get(appId) != false) {
                        set(appId, false)
                    } else {
                        set(appId, true)
                    }
                },
            )
        } else {
            copy(activityIds = activityIds.toMutableSet().apply {
                val entry = appId to activityId
                if (contains(entry)) {
                    remove(entry)
                } else {
                    add(entry)
                }
            })
        }
    }

    companion object {
        private val empty = ExcludeData(emptyMap(), emptySet())

        fun parse(exclude: String?): ExcludeData {
            if (exclude.isNullOrBlank()) {
                return empty
            }
            val appIds = HashMap<String, Boolean>()
            val activityIds = HashSet<Pair<String, String>>()
            exclude.split('\n')
                .filter { it.isNotBlank() }
                .forEach { value ->
                    if (value[0] == '!') {
                        val appId = value.substring(1)
                        if (appId.isValidAppId()) {
                            appIds[appId] = false
                        }
                    } else {
                        val parts = value.split('/', limit = 2)
                        val appId = parts[0]
                        if (appId.isValidAppId()) {
                            val activityId = parts.getOrNull(1)
                            if (activityId != null) {
                                if (activityId.isValidActivityId()) {
                                    activityIds.add(appId to activityId)
                                }
                            } else {
                                appIds[appId] = true
                            }
                        }
                    }
                }
            return ExcludeData(
                appIds = appIds,
                activityIds = activityIds,
            )
        }

        fun parse(exclude: String?, appId: String): ExcludeData {
            if (exclude.isNullOrBlank()) return empty
            return parse(exclude.split('\n').joinToString("\n") { "$appId/$it" })
        }
    }
}
