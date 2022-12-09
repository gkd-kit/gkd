package li.songe.gkd.data

import li.songe.selector.GkdSelector

class RuleMap {
    private val data = mutableMapOf<String, MutableMap<String, MutableList<GkdSelector>>>()
    fun load(subscription: Subscription) {
        subscription.appList.forEach {
            if (!data.containsKey(it.id)) {
                data[it.id] = mutableMapOf()
            }
            val m2 = data[it.id]!!
            it.groupList.forEach { group ->
                group.ruleList.forEach loop@{ rule ->
                    val name = (rule.activityId ?: group.activityId)
                    if (!m2.containsKey(name)) {
                        m2[name] = mutableListOf()
                    }
                    val list = m2[name]!!
                    list.add(GkdSelector.gkdSelectorParser(rule.match))
                }
            }
        }
    }

    fun count(packageName: String) = data[packageName]?.count() ?: 0
    fun count(packageName: String, activityName: String) =
        data[packageName]?.get(activityName)?.count() ?: 0

     suspend fun traverse(
        packageName: String,
        className: String,
        action: suspend (gkdSelector: GkdSelector) -> Any?
    ) {
        data[packageName]?.get(className)?.forEach {
            val result = action(it)
            if (result == true) {
                return
            }
        }
    }

}