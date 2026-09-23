package li.gkd.app.domain.rule

import li.gkd.app.text.UiStrings
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.edit
import li.gkd.db.SubsCategoryConfig

enum class CategorySetting(val label: String, val description: String) {
    FollowSubscription(UiStrings.category_follow_subscription, UiStrings.category_follow_subscription_description),
    Enabled(UiStrings.category_enabled, UiStrings.category_enabled_description),
    Disabled(UiStrings.category_disabled, UiStrings.category_disabled_description),
    GroupDefault(UiStrings.category_use_group_default, UiStrings.category_use_group_default_description),
}

object CategoryPolicy {
    fun setting(config: SubsCategoryConfig?): CategorySetting = when {
        config == null -> CategorySetting.FollowSubscription
        config.enable == true -> CategorySetting.Enabled
        config.enable == false -> CategorySetting.Disabled
        else -> CategorySetting.GroupDefault
    }

    fun validateEdit(
        subscription: RawSubscription,
        categoryKey: Int?,
        name: String,
    ) {
        require(subscription.isLocal) { UiStrings.remote_category_edit_unsupported }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { UiStrings.category_name_required }
        require(subscription.categories.none { it.key != categoryKey && it.name == trimmedName }) {
            UiStrings.category_name_duplicate
        }
        if (categoryKey == null) {
            require((subscription.categories.maxOfOrNull { it.key } ?: -1) < Int.MAX_VALUE) {
                UiStrings.category_key_exhausted
            }
        } else {
            check(subscription.categories.any { it.key == categoryKey }) { UiStrings.category_missing }
        }
    }

    fun previewEdit(
        subscription: RawSubscription,
        categoryKey: Int?,
        name: String,
        description: String,
    ): RawSubscription {
        validateEdit(subscription, categoryKey, name)
        val trimmedName = name.trim()
        val category = if (categoryKey == null) {
            val maxKey = subscription.categories.maxOfOrNull { it.key } ?: -1
            RawSubscription.RawCategory(maxKey + 1, trimmedName, null, null)
        } else {
            subscription.categories.find { it.key == categoryKey } ?: error(UiStrings.category_missing)
        }
        return subscription.edit {
            putCategory(category.copy(name = trimmedName, desc = description.trim().ifEmpty { null }))
        }
    }
}
