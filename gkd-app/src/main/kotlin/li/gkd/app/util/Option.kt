package li.gkd.app.util

import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkIcons

sealed interface Option<T> {
    val value: T
    val label: String
    val options: List<Option<T>>
}

sealed interface OptionIcon {
    val icon: ImageVector
}

fun <V, T : Option<V>> Iterable<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

sealed class AppSortOption(override val value: Int, override val label: String) : Option<Int> {
    override val options get() = objects

    data object ByAppName : AppSortOption(0, UiStrings.app_sort_name)
    data object ByActionTime : AppSortOption(2, UiStrings.app_sort_recent_trigger)
    data object ByUsedTime : AppSortOption(3, UiStrings.app_sort_recent_used)

    companion object {
        val objects by lazy { listOf(ByAppName, ByUsedTime, ByActionTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val label: String
) : Option<Long> {
    override val options get() = objects

    data object Pause : UpdateTimeOption(-1, UiStrings.action_pause)
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, UiStrings.update_interval_daily)
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3, UiStrings.update_interval_three_days)
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7, UiStrings.update_interval_weekly)

    companion object {
        val objects by lazy { listOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    override val label: String,
    override val icon: ImageVector
) : Option<Boolean?>, OptionIcon {
    override val options get() = objects

    data object FollowSystem : DarkThemeOption(null, UiStrings.theme_auto, GkIcons.BrightnessAuto)
    data object AlwaysEnable : DarkThemeOption(true, UiStrings.theme_dark, GkIcons.DarkMode)
    data object AlwaysDisable : DarkThemeOption(false, UiStrings.theme_light, GkIcons.LightMode)

    companion object {
        val objects by lazy { listOf(FollowSystem, AlwaysDisable, AlwaysEnable) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    override val options get() = objects

    data object FollowSubs : EnableGroupOption(null, UiStrings.category_follow_subscription)
    data object AllEnable : EnableGroupOption(true, UiStrings.rules_enable_all)
    data object AllDisable : EnableGroupOption(false, UiStrings.settings_all_off)

    companion object {
        val objects by lazy { listOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val label: String) : Option<Int> {
    override val options get() = objects

    data object ByDefault : RuleSortOption(0, UiStrings.rule_sort_default)
    data object ByActionTime : RuleSortOption(1, UiStrings.app_sort_recent_trigger)
    data object ByRuleName : RuleSortOption(2, UiStrings.rule_sort_name)

    companion object {
        val objects by lazy { listOf(ByDefault, ByActionTime, ByRuleName) }
    }
}

sealed class UpdateChannelOption(
    override val value: Int,
    override val label: String,
    val url: String
) : Option<Int> {
    override val options get() = objects

    data object Stable : UpdateChannelOption(
        0,
        UiStrings.update_channel_stable,
        "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
    )

    data object Beta : UpdateChannelOption(
        1,
        UiStrings.update_channel_beta,
        "https://registry.npmmirror.com/@gkd-kit/app-beta/latest/files/index.json"
    )

    companion object {
        val objects by lazy { listOf(Stable, Beta) }
    }
}

sealed interface BinaryOption : Option<Int> {
    fun include(flag: Int): Boolean = (value and flag) != 0
    fun invert(flag: Int): Int = value xor flag

    companion object {
        fun combine(options: Collection<BinaryOption>): Int {
            return options.fold(0) { a, b -> a or b.value }
        }
    }
}


sealed class AppGroupOption(
    override val value: Int,
    override val label: String
) : BinaryOption {
    override val options get() = allObjects

    data object SystemGroup : AppGroupOption(1 shl 0, UiStrings.apps_system)
    data object UserGroup : AppGroupOption(1 shl 1, UiStrings.apps_user)
    data object UnInstalledGroup : AppGroupOption(1 shl 2, UiStrings.apps_not_installed)

    companion object {
        val normalObjects by lazy { listOf(SystemGroup, UserGroup) }
        val allObjects by lazy { listOf(SystemGroup, UserGroup, UnInstalledGroup) }
    }
}

sealed class AutomatorModeOption(
    override val value: Int,
    override val label: String,
) : Option<Int> {
    override val options get() = objects

    data object A11yMode : AutomatorModeOption(1, UiStrings.a11y_label)
    data object AutomationMode : AutomatorModeOption(2, UiStrings.automation_label)

    companion object {
        val objects by lazy { listOf(A11yMode, AutomationMode) }
    }
}
