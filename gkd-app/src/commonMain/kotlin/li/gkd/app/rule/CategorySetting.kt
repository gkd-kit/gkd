package li.gkd.app.rule

import li.gkd.app.resources.*
import li.gkd.db.SubsCategoryConfig
import org.jetbrains.compose.resources.StringResource

enum class CategorySetting(
    val value: Boolean?,
    val labelResource: StringResource,
    val descriptionResource: StringResource,
) {
    FollowSubscription(null, Res.string.category_follow_subscription, Res.string.category_follow_subscription_description),
    Enabled(true, Res.string.category_enabled, Res.string.category_enabled_description),
    Disabled(false, Res.string.category_disabled, Res.string.category_disabled_description),
    GroupDefault(null, Res.string.category_use_group_default, Res.string.category_use_group_default_description);

    companion object {
        fun from(config: SubsCategoryConfig?): CategorySetting =
            config?.let { fromOverride(it.enable) } ?: FollowSubscription

        fun fromOverride(value: Boolean?): CategorySetting = when (value) {
            true -> Enabled
            false -> Disabled
            null -> GroupDefault
        }
    }
}
