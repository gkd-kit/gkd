package li.songe.gkd.util

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import li.songe.gkd.ui.component.PerfIcon

sealed interface Option<T> {
    val value: T
    val label: String
    val options: List<Option<T>>
}

sealed interface OptionIcon {
    val icon: ImageVector
}

sealed interface OptionMenuLabel {
    val menuLabel: String
}

fun <V, T : Option<V>> Iterable<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

fun Option<Boolean?>.toToggleableState() = when (value) {
    true -> ToggleableState.On
    false -> ToggleableState.Off
    null -> ToggleableState.Indeterminate
}

sealed class AppSortOption(override val value: Int, override val label: String) : Option<Int> {
    override val options get() = objects

    data object ByAppName : AppSortOption(0, "按应用名称")
    data object ByActionTime : AppSortOption(2, "按最近触发")
    data object ByUsedTime : AppSortOption(3, "按最近使用")

    companion object {
        val objects by lazy { listOf(ByAppName, ByUsedTime, ByActionTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val label: String
) : Option<Long> {
    override val options get() = objects

    data object Pause : UpdateTimeOption(-1, "暂停")
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, "每天")
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3, "每3天")
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7, "每7天")

    companion object {
        val objects by lazy { listOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    override val label: String,
    override val menuLabel: String,
    override val icon: ImageVector
) : Option<Boolean?>, OptionIcon, OptionMenuLabel {
    override val options get() = objects

    data object FollowSystem : DarkThemeOption(null, "自动", "自动", PerfIcon.AutoMode)
    data object AlwaysEnable : DarkThemeOption(true, "启用", "深色", PerfIcon.DarkMode)
    data object AlwaysDisable : DarkThemeOption(false, "关闭", "浅色", PerfIcon.LightMode)

    companion object {
        val objects by lazy { listOf(FollowSystem, AlwaysEnable, AlwaysDisable) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    override val options get() = objects

    data object FollowSubs : EnableGroupOption(null, "跟随订阅")
    data object AllEnable : EnableGroupOption(true, "全部启用")
    data object AllDisable : EnableGroupOption(false, "全部关闭")

    companion object {
        val objects by lazy { listOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val label: String) : Option<Int> {
    override val options get() = objects

    data object ByDefault : RuleSortOption(0, "按默认顺序")
    data object ByActionTime : RuleSortOption(1, "按最近触发")
    data object ByRuleName : RuleSortOption(2, "按规则名称")

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
        "稳定版",
        "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
    )

    data object Beta : UpdateChannelOption(
        1,
        "测试版",
        "https://registry.npmmirror.com/@gkd-kit/app-beta/latest/files/index.json"
    )

    companion object {
        val objects by lazy { listOf(Stable, Beta) }
    }
}
