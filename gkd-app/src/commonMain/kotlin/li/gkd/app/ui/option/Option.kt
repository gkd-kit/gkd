package li.gkd.app.ui.option

import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.app.AppGroupFlags
import li.gkd.app.model.Option
import li.gkd.app.resources.*
import li.gkd.app.settings.SubscriptionUpdateInterval
import li.gkd.app.ui.component.GkIcons
import org.jetbrains.compose.resources.StringResource

sealed interface OptionIcon {
    val icon: ImageVector
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val labelResource: StringResource
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
    override val labelResource: StringResource,
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
    override val labelResource: StringResource
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
