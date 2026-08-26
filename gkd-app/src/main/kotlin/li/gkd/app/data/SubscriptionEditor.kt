package li.songe.gkd.data

class SubscriptionEditor(subscription: RawSubscription) {
    private val subscriptionId = subscription.id
    private var current = subscription

    fun update(transform: RawSubscription.() -> RawSubscription) {
        setCurrent(current.transform())
    }

    fun updateApp(
        appId: String,
        transform: RawSubscription.RawApp.() -> RawSubscription.RawApp,
    ): Boolean {
        val newApps = current.apps.updateFirstOrNull(
            predicate = { it.id == appId },
            transform = { it.transform() },
        ) ?: return false
        if (newApps !== current.apps) {
            setCurrent(current.copy(apps = newApps))
        }
        return true
    }

    private fun putApp(app: RawSubscription.RawApp): RawSubscription.RawApp? {
        val previous = current.apps.find { it.id == app.id }
        if (previous == null) {
            setCurrent(current.copy(apps = current.apps + app))
        } else {
            updateApp(app.id) { app }
        }
        return previous
    }

    fun mergeApp(app: RawSubscription.RawApp): RawSubscription.RawApp {
        val currentApp = current.apps.find { it.id == app.id }
        if (currentApp == null) {
            requireGroupNamesAvailable(emptyList(), app.groups)
            return app.copy(
                groups = normalizeAppGroupKeys(emptyList(), app.groups),
            ).also(::putApp)
        }
        return appendAppGroups(app, app.groups)
    }

    fun appendAppGroups(
        targetApp: RawSubscription.RawApp,
        groups: List<RawSubscription.RawAppGroup>,
    ): RawSubscription.RawApp {
        val app = current.apps.find { it.id == targetApp.id } ?: targetApp
        requireGroupNamesAvailable(app.groups, groups)
        val normalizedGroups = normalizeAppGroupKeys(app.groups, groups)
        return app.copy(groups = app.groups + normalizedGroups).also(::putApp)
    }

    private fun removeApp(appId: String): RawSubscription.RawApp? {
        val app = current.apps.find { it.id == appId } ?: return null
        setCurrent(current.copy(apps = current.apps.filterNot { it.id == appId }))
        return app
    }

    fun updateCategory(
        categoryKey: Int,
        transform: RawSubscription.RawCategory.() -> RawSubscription.RawCategory,
    ): Boolean {
        val newCategories = current.categories.updateFirstOrNull(
            predicate = { it.key == categoryKey },
            transform = { it.transform() },
        ) ?: return false
        if (newCategories !== current.categories) {
            setCurrent(current.copy(categories = newCategories))
        }
        return true
    }

    fun putCategory(category: RawSubscription.RawCategory): RawSubscription.RawCategory? {
        val previous = current.categories.find { it.key == category.key }
        if (previous == null) {
            setCurrent(current.copy(categories = current.categories + category))
        } else {
            updateCategory(category.key) { category }
        }
        return previous
    }

    fun removeCategory(categoryKey: Int): RawSubscription.RawCategory? {
        val category = current.categories.find { it.key == categoryKey } ?: return null
        setCurrent(
            current.copy(categories = current.categories.filterNot { it.key == categoryKey }),
        )
        return category
    }

    private fun updateGlobalGroup(
        groupKey: Int,
        transform: RawSubscription.RawGlobalGroup.() -> RawSubscription.RawGlobalGroup,
    ): Boolean {
        val newGroups = current.globalGroups.updateFirstOrNull(
            predicate = { it.key == groupKey },
            transform = { it.transform() },
        ) ?: return false
        if (newGroups !== current.globalGroups) {
            setCurrent(current.copy(globalGroups = newGroups))
        }
        return true
    }

    private fun putGlobalGroup(
        group: RawSubscription.RawGlobalGroup,
    ): RawSubscription.RawGlobalGroup? {
        val previous = current.globalGroups.find { it.key == group.key }
        if (previous == null) {
            setCurrent(current.copy(globalGroups = current.globalGroups + group))
        } else {
            updateGlobalGroup(group.key) { group }
        }
        return previous
    }

    fun appendGlobalGroup(
        group: RawSubscription.RawGlobalGroup,
    ): RawSubscription.RawGlobalGroup {
        requireGroupNamesAvailable(current.globalGroups, listOf(group))
        val normalizedGroup = normalizeGlobalGroupKey(current.globalGroups, group)
        putGlobalGroup(normalizedGroup)
        return normalizedGroup
    }

    fun removeGlobalGroups(
        predicate: (RawSubscription.RawGlobalGroup) -> Boolean,
    ): List<RawSubscription.RawGlobalGroup> {
        val (removed, remaining) = current.globalGroups.partition(predicate)
        if (removed.isNotEmpty()) {
            setCurrent(current.copy(globalGroups = remaining))
        }
        return removed
    }

    private fun updateAppGroup(
        appId: String,
        groupKey: Int,
        transform: RawSubscription.RawAppGroup.() -> RawSubscription.RawAppGroup,
    ): Boolean {
        var groupFound = false
        updateApp(appId) {
            val newGroups = groups.updateFirstOrNull(
                predicate = { it.key == groupKey },
                transform = { group -> group.transform() },
            ) ?: return@updateApp this
            groupFound = true
            if (newGroups === groups) {
                this
            } else {
                copy(groups = newGroups)
            }
        }
        return groupFound
    }

    fun replaceAppGroup(
        targetApp: RawSubscription.RawApp,
        groupKey: Int,
        expectedGroup: RawSubscription.RawAppGroup,
        newGroup: RawSubscription.RawAppGroup,
    ) {
        require(newGroup.key == groupKey) { "规则key与当前规则不一致" }
        val currentApp = current.apps.find { it.id == targetApp.id }
        if (currentApp == null) {
            putApp(targetApp.copy(groups = listOf(newGroup)))
            return
        }
        val updated = updateAppGroup(targetApp.id, groupKey) {
            if (this != expectedGroup) error("规则已发生变化，请重新编辑")
            requireGroupNamesAvailable(
                currentApp.groups.filterNot { it.key == groupKey },
                listOf(newGroup),
            )
            newGroup
        }
        if (!updated) error("规则已不存在")
    }

    fun replaceGlobalGroup(
        groupKey: Int,
        expectedGroup: RawSubscription.RawGlobalGroup,
        newGroup: RawSubscription.RawGlobalGroup,
    ) {
        require(newGroup.key == groupKey) { "规则key与当前规则不一致" }
        val updated = updateGlobalGroup(groupKey) {
            if (this != expectedGroup) error("规则已发生变化，请重新编辑")
            requireGroupNamesAvailable(
                current.globalGroups.filterNot { it.key == groupKey },
                listOf(newGroup),
            )
            newGroup
        }
        if (!updated) error("规则已不存在")
    }

    fun removeAppGroups(
        appId: String,
        removeAppIfEmpty: Boolean = false,
        predicate: (RawSubscription.RawAppGroup) -> Boolean,
    ): List<RawSubscription.RawAppGroup> {
        val app = current.apps.find { it.id == appId } ?: return emptyList()
        val (removed, remaining) = app.groups.partition(predicate)
        if (removed.isEmpty()) return emptyList()
        if (removeAppIfEmpty && remaining.isEmpty()) {
            removeApp(appId)
        } else {
            updateApp(appId) { copy(groups = remaining) }
        }
        return removed
    }

    fun build(): RawSubscription = current

    private fun setCurrent(subscription: RawSubscription) {
        require(subscription.id == subscriptionId) {
            "订阅id不可修改: $subscriptionId -> ${subscription.id}"
        }
        current = subscription
    }

    private fun requireGroupNamesAvailable(
        existingGroups: List<RawSubscription.RawGroupProps>,
        newGroups: List<RawSubscription.RawGroupProps>,
    ) {
        val usedNames = existingGroups.mapTo(mutableSetOf()) { it.name }
        newGroups.forEach { group ->
            if (!usedNames.add(group.name)) {
                error("已存在同名「${group.name}」规则")
            }
        }
    }

    private fun normalizeAppGroupKeys(
        existingGroups: List<RawSubscription.RawAppGroup>,
        newGroups: List<RawSubscription.RawAppGroup>,
    ): List<RawSubscription.RawAppGroup> {
        val usedKeys = existingGroups.mapTo(mutableSetOf()) { it.key }
        return newGroups.map { group ->
            if (usedKeys.add(group.key)) {
                group
            } else {
                val newKey = requireNotNull(usedKeys.maxOrNull()) + 1
                usedKeys.add(newKey)
                group.copy(key = newKey)
            }
        }
    }

    private fun normalizeGlobalGroupKey(
        existingGroups: List<RawSubscription.RawGlobalGroup>,
        group: RawSubscription.RawGlobalGroup,
    ): RawSubscription.RawGlobalGroup {
        if (existingGroups.none { it.key == group.key }) return group
        return group.copy(key = existingGroups.maxOf { it.key } + 1)
    }
}

fun RawSubscription.edit(
    block: SubscriptionEditor.() -> Unit,
): RawSubscription = SubscriptionEditor(this).apply(block).build()

private inline fun <T> List<T>.updateFirstOrNull(
    predicate: (T) -> Boolean,
    transform: (T) -> T,
): List<T>? {
    val index = indexOfFirst(predicate)
    if (index < 0) return null
    val current = this[index]
    val updated = transform(current)
    return if (updated == current) {
        this
    } else {
        toMutableList().apply { set(index, updated) }
    }
}
