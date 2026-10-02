package li.gkd.app.ui.option

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.app.AppGroupFlags
import li.gkd.app.app.AppSort
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_label
import li.gkd.app.resources.action_pause
import li.gkd.app.resources.app_sort_name
import li.gkd.app.resources.app_sort_recent_trigger
import li.gkd.app.resources.app_sort_recent_used
import li.gkd.app.resources.apps_not_installed
import li.gkd.app.resources.apps_system
import li.gkd.app.resources.apps_user
import li.gkd.app.resources.automation_label
import li.gkd.app.resources.category_follow_subscription
import li.gkd.app.resources.rule_sort_default
import li.gkd.app.resources.rule_sort_name
import li.gkd.app.resources.rules_enable_all
import li.gkd.app.resources.settings_all_off
import li.gkd.app.resources.snapshot_view_app
import li.gkd.app.resources.snapshot_view_time
import li.gkd.app.resources.theme_auto
import li.gkd.app.resources.theme_dark
import li.gkd.app.resources.theme_light
import li.gkd.app.resources.update_channel_beta
import li.gkd.app.resources.update_channel_stable
import li.gkd.app.resources.update_interval_daily
import li.gkd.app.resources.update_interval_three_days
import li.gkd.app.resources.update_interval_weekly
import li.gkd.app.rule.RuleSort
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.settings.SubscriptionUpdateInterval
import li.gkd.app.settings.UpdateChannel
import li.gkd.app.snapshot.SnapshotDisplayMode
import li.gkd.app.ui.component.GkIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed interface Option<T> {
    val value: T
    val labelKey: StringResource
    val label: String
        @Composable get() = stringResource(labelKey)
    val options: List<Option<T>>
}

sealed interface OptionIcon {
    val icon: ImageVector
}

fun <V, T : Option<V>> Iterable<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

sealed class SnapshotDisplayModeOption(
    override val value: Int,
    override val labelKey: StringResource
) : Option<Int> {
    override val options get() = objects

    data object ByTime :
        SnapshotDisplayModeOption(SnapshotDisplayMode.ByTime.value, Res.string.snapshot_view_time)

    data object ByApp :
        SnapshotDisplayModeOption(SnapshotDisplayMode.ByApp.value, Res.string.snapshot_view_app)

    companion object {
        val objects by lazy { listOf(ByTime, ByApp) }
    }
}

sealed class AppSortOption(override val value: Int, override val labelKey: StringResource) :
    Option<Int> {
    override val options get() = objects

    data object ByAppName : AppSortOption(AppSort.ByAppName.value, Res.string.app_sort_name)
    data object ByActionTime :
        AppSortOption(AppSort.ByActionTime.value, Res.string.app_sort_recent_trigger)

    data object ByUsedTime :
        AppSortOption(AppSort.ByUsedTime.value, Res.string.app_sort_recent_used)

    companion object {
        val objects by lazy { listOf(ByAppName, ByUsedTime, ByActionTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val labelKey: StringResource
) : Option<Long> {
    override val options get() = objects

    data object Pause : UpdateTimeOption(SubscriptionUpdateInterval.Paused, Res.string.action_pause)
    data object Everyday :
        UpdateTimeOption(SubscriptionUpdateInterval.Daily, Res.string.update_interval_daily)

    data object Every3Days : UpdateTimeOption(
        SubscriptionUpdateInterval.EveryThreeDays,
        Res.string.update_interval_three_days
    )

    data object Every7Days :
        UpdateTimeOption(SubscriptionUpdateInterval.Weekly, Res.string.update_interval_weekly)

    companion object {
        val objects by lazy { listOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    override val labelKey: StringResource,
    override val icon: ImageVector
) : Option<Boolean?>, OptionIcon {
    override val options get() = objects

    data object FollowSystem : DarkThemeOption(null, Res.string.theme_auto, GkIcons.BrightnessAuto)
    data object AlwaysEnable : DarkThemeOption(true, Res.string.theme_dark, GkIcons.DarkMode)
    data object AlwaysDisable : DarkThemeOption(false, Res.string.theme_light, GkIcons.LightMode)

    companion object {
        val objects by lazy { listOf(FollowSystem, AlwaysDisable, AlwaysEnable) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    override val labelKey: StringResource
) : Option<Boolean?> {
    override val options get() = objects

    data object FollowSubs : EnableGroupOption(null, Res.string.category_follow_subscription)
    data object AllEnable : EnableGroupOption(true, Res.string.rules_enable_all)
    data object AllDisable : EnableGroupOption(false, Res.string.settings_all_off)

    companion object {
        val objects by lazy { listOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val labelKey: StringResource) :
    Option<Int> {
    override val options get() = objects

    data object ByDefault : RuleSortOption(RuleSort.ByDefault.value, Res.string.rule_sort_default)
    data object ByActionTime :
        RuleSortOption(RuleSort.ByActionTime.value, Res.string.app_sort_recent_trigger)

    data object ByRuleName : RuleSortOption(RuleSort.ByRuleName.value, Res.string.rule_sort_name)

    companion object {
        val objects by lazy { listOf(ByDefault, ByActionTime, ByRuleName) }
    }
}

sealed class UpdateChannelOption(
    override val value: Int,
    override val labelKey: StringResource,
    val url: String
) : Option<Int> {
    override val options get() = objects

    data object Stable : UpdateChannelOption(
        UpdateChannel.Stable.value,
        Res.string.update_channel_stable,
        "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
    )

    data object Beta : UpdateChannelOption(
        UpdateChannel.Beta.value,
        Res.string.update_channel_beta,
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
    override val labelKey: StringResource
) : BinaryOption {
    override val options get() = allObjects

    data object SystemGroup : AppGroupOption(AppGroupFlags.System, Res.string.apps_system)
    data object UserGroup : AppGroupOption(AppGroupFlags.User, Res.string.apps_user)
    data object UnInstalledGroup :
        AppGroupOption(AppGroupFlags.Uninstalled, Res.string.apps_not_installed)

    companion object {
        val normalObjects by lazy { listOf(SystemGroup, UserGroup) }
        val allObjects by lazy { listOf(SystemGroup, UserGroup, UnInstalledGroup) }
    }
}

sealed class AutomatorModeOption(
    override val value: Int,
    override val labelKey: StringResource,
) : Option<Int> {
    override val options get() = objects

    data object A11yMode : AutomatorModeOption(AutomatorMode.A11y.value, Res.string.a11y_label)
    data object AutomationMode :
        AutomatorModeOption(AutomatorMode.Automation.value, Res.string.automation_label)

    companion object {
        val objects by lazy { listOf(A11yMode, AutomationMode) }
    }
}
