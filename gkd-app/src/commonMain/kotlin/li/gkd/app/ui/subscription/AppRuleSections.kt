package li.gkd.app.ui.subscription

import li.gkd.app.subscription.RawSubscription

enum class AppRuleSectionType { Global, Category, Uncategorized, Ungrouped }

sealed interface AppRuleCategorySelection {
    data object All : AppRuleCategorySelection
    data object Global : AppRuleCategorySelection
    data object FocusedRule : AppRuleCategorySelection
    data class Category(val name: String?) : AppRuleCategorySelection
}

fun resolveAppRuleCategory(
    selection: AppRuleCategorySelection,
    sections: List<AppRuleSection>,
    focusTarget: Triple<Long, Int, Int>?,
): AppRuleCategorySelection.Category? {
    val category = when (selection) {
        AppRuleCategorySelection.All, AppRuleCategorySelection.Global -> null
        is AppRuleCategorySelection.Category -> selection
        AppRuleCategorySelection.FocusedRule -> sections.find { section ->
            section.showHeader && section.subscriptions.any { (subscription, groups) ->
                groups.any { Triple(subscription.id, it.groupType, it.key) == focusTarget }
            }
        }?.let { AppRuleCategorySelection.Category(it.categoryName) }
    }
    return category?.takeIf { selected ->
        sections.any { it.showHeader && it.categoryName == selected.name }
    }
}

fun filterAppRuleSections(
    sections: List<AppRuleSection>,
    category: AppRuleCategorySelection.Category?,
    globalOnly: Boolean = false,
): List<AppRuleSection> = when {
    globalOnly -> sections.filter { it.type == AppRuleSectionType.Global }
    category == null -> sections
    else -> sections.filter { it.showHeader && it.categoryName == category.name }
}

data class AppRuleSection(
    val type: AppRuleSectionType,
    val categoryName: String? = null,
    val subscriptions: List<Pair<RawSubscription, List<RawSubscription.RawGroupProps>>>,
) {
    val key: Pair<AppRuleSectionType, String?> = type to categoryName
    val showHeader: Boolean
        get() = type == AppRuleSectionType.Category || type == AppRuleSectionType.Uncategorized
    val groupCount: Int get() = subscriptions.sumOf { it.second.size }

    fun subscriptionKey(subsId: Long) = key to subsId
}

/** Input order is the subscription order; each group's position already reflects the chosen sort. */
fun buildAppRuleSections(
    subscriptions: List<Pair<RawSubscription, List<RawSubscription.RawGroupProps>>>,
    byCategory: Boolean,
): List<AppRuleSection> = buildList {
    if (byCategory) {
        val groupedSubscriptions = subscriptions.map { (subscription, groups) ->
            subscription to groups.groupBy { group ->
                when (group) {
                    is RawSubscription.RawGlobalGroup -> AppRuleSectionType.Global to null
                    is RawSubscription.RawAppGroup -> {
                        val name = subscription.getCategory(group.name)?.name
                        (if (name == null) AppRuleSectionType.Uncategorized else AppRuleSectionType.Category) to name
                    }
                }
            }
        }
        fun append(type: AppRuleSectionType, categoryName: String? = null) {
            val members = groupedSubscriptions.mapNotNull { (subscription, groups) ->
                groups[type to categoryName]?.let { subscription to it }
            }
            if (members.isNotEmpty()) add(AppRuleSection(type, categoryName, members))
        }
        append(AppRuleSectionType.Global)
        subscriptions.flatMap { it.first.categories }.map { it.name }.distinct().forEach { name ->
            append(AppRuleSectionType.Category, name)
        }
        append(AppRuleSectionType.Uncategorized)
    } else {
        val members = subscriptions.mapNotNull { (subscription, groups) ->
            groups.takeIf { it.isNotEmpty() }?.let {
                subscription to it.sortedBy { group -> group is RawSubscription.RawAppGroup }
            }
        }
        if (members.isNotEmpty()) add(AppRuleSection(AppRuleSectionType.Ungrouped, subscriptions = members))
    }
}
