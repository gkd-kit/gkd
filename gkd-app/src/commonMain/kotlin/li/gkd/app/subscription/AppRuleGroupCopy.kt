package li.gkd.app.subscription

import li.gkd.app.subscription.RawSubscription.RawAppGroup

fun prepareAppGroupsForCopy(groups: List<Pair<Long, RawAppGroup>>): List<RawAppGroup> {
    val firstSubsId = groups.firstOrNull()?.first ?: return emptyList()
    if (groups.all { (subsId, _) -> subsId == firstSubsId }) return groups.map { it.second }

    val usedKeys = mutableSetOf<Int>()
    val copiedKeys = groups.associate { (subsId, group) ->
        val key =
            if (usedKeys.add(group.key)) {
                group.key
            } else {
                var candidate = requireNotNull(usedKeys.maxOrNull()) + 1
                while (!usedKeys.add(candidate)) {
                    candidate++
                }
                candidate
            }
        (subsId to group.key) to key
    }

    return groups.map { (subsId, group) ->
        group.copy(
            key = copiedKeys.getValue(subsId to group.key),
            // A combined export can only identify selected groups from the same subscription
            scopeKeys =
                group.scopeKeys?.mapNotNull { scopeKey ->
                    if (scopeKey == group.key) null else copiedKeys[subsId to scopeKey]
                },
        )
    }
}
