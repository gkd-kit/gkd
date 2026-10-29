package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import li.gkd.app.rule.RuleStateOperation
import li.gkd.app.rule.RuleStateValues

@Serializable
data class RuleStatePolicy(
    val maximum: Int?,
    val cooldown: Long,
    val matchDelay: Long,
    val matchTime: Long?,
    val actionDelay: Long,
)

@Serializable
data class RuleStateRecord(
    val operation: RuleStateOperation,
    val time: Long,
    val runtime: Long,
    val execution: Long,
    val identity: String,
    val countCell: Long,
    val cooldownCell: Long,
    val before: RuleStateValues,
    val after: RuleStateValues,
    val policy: RuleStatePolicy,
    val prerequisites: List<Long>,
    val lastTrigger: Long?,
    val status: String? = null,
)
