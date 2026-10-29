package li.gkd.app.subscription

class SubscriptionEditor(
    subscription: RawSubscription,
) {
    private var current = subscription

    fun update(transform: RawSubscription.() -> RawSubscription) {
        val next = current.transform()
        require(next.id == current.id) {
            throw SubscriptionException(
                SubscriptionFailureReason.SubscriptionIdImmutable,
                listOf(current.id.toString(), next.id.toString()),
            )
        }
        current = next
    }

    fun updateApp(
        appId: String,
        transform: RawSubscription.RawApp.() -> RawSubscription.RawApp,
    ): Boolean {
        val newApps =
            current.apps.updateFirstOrNull(
                predicate = { it.id == appId },
                transform = { it.transform() },
            ) ?: return false
        if (newApps !== current.apps) {
            current = current.copy(apps = newApps)
        }
        return true
    }

    private fun putApp(app: RawSubscription.RawApp): RawSubscription.RawApp? {
        val previous = current.apps.find { it.id == app.id }
        if (previous == null) {
            current = current.copy(apps = current.apps + app)
        } else {
            updateApp(app.id) { app }
        }
        return previous
    }

    fun mergeApp(app: RawSubscription.RawApp): SubscriptionEditResult<RawSubscription.RawApp> {
        val currentApp = current.apps.find { it.id == app.id }
        if (currentApp == null) {
            validateGroupNames(emptyList(), app.groups)?.let {
                return it
            }
            return SubscriptionEditResult.Success(
                app.copy(
                        groups = normalizeAppGroupKeys(emptyList(), app.groups),
                    )
                    .also(::putApp),
            )
        }
        return appendAppGroups(app, app.groups)
    }

    fun appendAppGroups(
        targetApp: RawSubscription.RawApp,
        groups: List<RawSubscription.RawAppGroup>,
    ): SubscriptionEditResult<RawSubscription.RawApp> {
        val app = current.apps.find { it.id == targetApp.id } ?: targetApp
        validateGroupNames(app.groups, groups)?.let {
            return it
        }
        val normalizedGroups = normalizeAppGroupKeys(app.groups, groups)
        return SubscriptionEditResult.Success(
            app.copy(groups = app.groups + normalizedGroups).also(::putApp),
        )
    }

    private fun removeApp(appId: String): RawSubscription.RawApp? {
        val app = current.apps.find { it.id == appId } ?: return null
        current = current.copy(apps = current.apps.filterNot { it.id == appId })
        return app
    }

    fun updateCategory(
        categoryKey: Int,
        transform: RawSubscription.RawCategory.() -> RawSubscription.RawCategory,
    ): Boolean {
        val newCategories =
            current.categories.updateFirstOrNull(
                predicate = { it.key == categoryKey },
                transform = { it.transform() },
            ) ?: return false
        if (newCategories !== current.categories) {
            current = current.copy(categories = newCategories)
        }
        return true
    }

    fun putCategory(category: RawSubscription.RawCategory): RawSubscription.RawCategory? {
        val previous = current.categories.find { it.key == category.key }
        if (previous == null) {
            current = current.copy(categories = current.categories + category)
        } else {
            updateCategory(category.key) { category }
        }
        return previous
    }

    fun removeCategory(categoryKey: Int): RawSubscription.RawCategory? {
        val category = current.categories.find { it.key == categoryKey } ?: return null
        current = current.copy(categories = current.categories.filterNot { it.key == categoryKey })
        return category
    }

    private fun updateGlobalGroup(
        groupKey: Int,
        transform: RawSubscription.RawGlobalGroup.() -> RawSubscription.RawGlobalGroup,
    ): Boolean {
        val newGroups =
            current.globalGroups.updateFirstOrNull(
                predicate = { it.key == groupKey },
                transform = { it.transform() },
            ) ?: return false
        if (newGroups !== current.globalGroups) {
            current = current.copy(globalGroups = newGroups)
        }
        return true
    }

    private fun putGlobalGroup(
        group: RawSubscription.RawGlobalGroup,
    ): RawSubscription.RawGlobalGroup? {
        val previous = current.globalGroups.find { it.key == group.key }
        if (previous == null) {
            current = current.copy(globalGroups = current.globalGroups + group)
        } else {
            updateGlobalGroup(group.key) { group }
        }
        return previous
    }

    fun appendGlobalGroup(
        group: RawSubscription.RawGlobalGroup,
    ): SubscriptionEditResult<RawSubscription.RawGlobalGroup> {
        validateGroupNames(current.globalGroups, listOf(group))?.let {
            return it
        }
        val normalizedGroup = normalizeGlobalGroupKey(current.globalGroups, group)
        putGlobalGroup(normalizedGroup)
        return SubscriptionEditResult.Success(normalizedGroup)
    }

    fun removeGlobalGroups(
        predicate: (RawSubscription.RawGlobalGroup) -> Boolean,
    ): List<RawSubscription.RawGlobalGroup> {
        val (removed, remaining) = current.globalGroups.partition(predicate)
        if (removed.isNotEmpty()) {
            current = current.copy(globalGroups = remaining)
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
            val newGroups =
                groups.updateFirstOrNull(
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
    ): SubscriptionEditResult<Unit> {
        require(newGroup.key == groupKey)
        val currentApp = current.apps.find { it.id == targetApp.id }
        if (currentApp == null) {
            putApp(targetApp.copy(groups = listOf(newGroup)))
            return SubscriptionEditResult.Success(Unit)
        }
        val group =
            currentApp.groups.find { it.key == groupKey }
                ?: return SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleMissing)
        if (group != expectedGroup)
            return SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleEditConflict)
        validateGroupNames(currentApp.groups.filterNot { it.key == groupKey }, listOf(newGroup))
            ?.let {
                return it
            }
        updateAppGroup(targetApp.id, groupKey) { newGroup }
        return SubscriptionEditResult.Success(Unit)
    }

    fun replaceGlobalGroup(
        groupKey: Int,
        expectedGroup: RawSubscription.RawGlobalGroup,
        newGroup: RawSubscription.RawGlobalGroup,
    ): SubscriptionEditResult<Unit> {
        require(newGroup.key == groupKey)
        val group =
            current.globalGroups.find { it.key == groupKey }
                ?: return SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleMissing)
        if (group != expectedGroup)
            return SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleEditConflict)
        validateGroupNames(current.globalGroups.filterNot { it.key == groupKey }, listOf(newGroup))
            ?.let {
                return it
            }
        updateGlobalGroup(groupKey) { newGroup }
        return SubscriptionEditResult.Success(Unit)
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

    private fun validateGroupNames(
        existingGroups: List<RawSubscription.RawGroupProps>,
        newGroups: List<RawSubscription.RawGroupProps>,
    ): SubscriptionEditResult.Rejected? {
        val usedNames = existingGroups.mapTo(mutableSetOf()) { it.name }
        newGroups.forEach { group ->
            if (!usedNames.add(group.name)) {
                return SubscriptionEditResult.Rejected(
                    SubscriptionFailureReason.RuleNameDuplicate,
                    listOf(group.name),
                )
            }
        }
        return null
    }

    private fun normalizeAppGroupKeys(
        existingGroups: List<RawSubscription.RawAppGroup>,
        newGroups: List<RawSubscription.RawAppGroup>,
    ): List<RawSubscription.RawAppGroup> {
        val usedKeys = existingGroups.mapTo(mutableSetOf()) { it.key }
        val assignedGroups = newGroups.map { group ->
            val newKey =
                if (usedKeys.add(group.key)) {
                    group.key
                } else {
                    (requireNotNull(usedKeys.maxOrNull()) + 1).also(usedKeys::add)
                }
            group to newKey
        }

        return assignedGroups.map { (group, newKey) ->
            group.copy(
                key = newKey,
                scopeKeys =
                    group.scopeKeys?.map { scopeKey ->
                        assignedGroups
                            .find { (candidate, _) ->
                                candidate !== group && candidate.key == scopeKey
                            }
                            ?.second ?: scopeKey
                    },
            )
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
