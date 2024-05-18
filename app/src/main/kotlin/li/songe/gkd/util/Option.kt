package li.songe.gkd.util

sealed interface Option<T> {
    val value: T
    val label: String
}

fun <V, T : Option<V>> Array<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

@Suppress("UNCHECKED_CAST")
val <T> Option<T>.allSubObject: Array<Option<T>>
    get() = when (this) {
        is SortTypeOption -> SortTypeOption.allSubObject
        is UpdateTimeOption -> UpdateTimeOption.allSubObject
        is DarkThemeOption -> DarkThemeOption.allSubObject
        is EnableGroupOption -> EnableGroupOption.allSubObject
        is RuleSortOption -> RuleSortOption.allSubObject
    } as Array<Option<T>>

sealed class SortTypeOption(override val value: Int, override val label: String) : Option<Int> {
    data object SortByName : SortTypeOption(0, "按名称")
    data object SortByAppMtime : SortTypeOption(1, "按更新时间")
    data object SortByTriggerTime : SortTypeOption(2, "按触发时间")

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubObject by lazy { arrayOf(SortByName, SortByAppMtime, SortByTriggerTime) }
    }
}

sealed class UpdateTimeOption(override val value: Long, override val label: String) : Option<Long> {
    data object Pause : UpdateTimeOption(-1, "暂停")
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, "每天")
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3, "每3天")
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7, "每7天")

    companion object {
        val allSubObject by lazy { arrayOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(override val value: Boolean?, override val label: String) :
    Option<Boolean?> {
    data object FollowSystem : DarkThemeOption(null, "跟随系统")
    data object AlwaysEnable : DarkThemeOption(true, "总是启用")
    data object AlwaysDisable : DarkThemeOption(false, "总是关闭")

    companion object {
        val allSubObject by lazy { arrayOf(FollowSystem, AlwaysEnable, AlwaysDisable) }
    }
}

sealed class EnableGroupOption(override val value: Boolean?, override val label: String) :
    Option<Boolean?> {
    data object FollowSubs : DarkThemeOption(null, "跟随订阅")
    data object AllEnable : DarkThemeOption(true, "全部启用")
    data object AllDisable : DarkThemeOption(false, "全部关闭")

    companion object {
        val allSubObject by lazy { arrayOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val label: String) : Option<Int> {
    data object Default : RuleSortOption(0, "按订阅顺序")
    data object ByTime : RuleSortOption(1, "按触发时间")
    data object ByName : RuleSortOption(2, "按名称")

    companion object {
        val allSubObject by lazy { arrayOf(Default, ByTime, ByName) }
    }
}
