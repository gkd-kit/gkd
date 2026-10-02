package li.gkd.app.rule

import li.gkd.db.SubsCategoryConfig

enum class CategorySetting { FollowSubscription, Enabled, Disabled, GroupDefault }

object CategoryPolicy {
    fun setting(config: SubsCategoryConfig?): CategorySetting = when {
        config == null -> CategorySetting.FollowSubscription
        config.enable == true -> CategorySetting.Enabled
        config.enable == false -> CategorySetting.Disabled
        else -> CategorySetting.GroupDefault
    }

}
