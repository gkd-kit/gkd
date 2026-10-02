package li.gkd.app.rule

import li.gkd.app.subscription.RawSubscription
import li.gkd.db.RuleGroupType

sealed interface RuleGroupTarget {
    val subsId: Long
    val groupKey: Int
    val appId: String?
    val pageAppId: String?

    val groupType: Int
        get() = when (this) {
            is App -> RuleGroupType.App
            is Global -> RuleGroupType.Global
        }

    data class App(
        override val subsId: Long,
        override val appId: String,
        override val groupKey: Int,
    ) : RuleGroupTarget {
        override val pageAppId: String = appId
    }

    data class Global(
        override val subsId: Long,
        override val groupKey: Int,
        override val pageAppId: String? = null,
    ) : RuleGroupTarget {
        override val appId: String? = null
    }
}

fun RawSubscription.RawGroupProps.toRuleGroupTarget(
    subsId: Long,
    appId: String? = null,
): RuleGroupTarget = when (this) {
    is RawSubscription.RawAppGroup -> RuleGroupTarget.App(
        subsId = subsId,
        appId = appId ?: error("require appId"),
        groupKey = key,
    )

    is RawSubscription.RawGlobalGroup -> RuleGroupTarget.Global(
        subsId = subsId,
        groupKey = key,
        pageAppId = appId,
    )
}
