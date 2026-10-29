package li.gkd.db

sealed interface SubsGroupConfig {
    val subsId: Long
    val groupKey: Int
    val enable: Boolean?
    val exclude: String
}

fun SubsGroupConfig.withEnable(enable: Boolean?): SubsGroupConfig =
    when (this) {
        is SubsAppGroupConfig -> copy(enable = enable)
        is SubsGlobalGroupConfig -> copy(enable = enable)
    }

fun SubsGroupConfig.withExclude(exclude: String): SubsGroupConfig =
    when (this) {
        is SubsAppGroupConfig -> copy(exclude = exclude)
        is SubsGlobalGroupConfig -> copy(exclude = exclude)
    }
