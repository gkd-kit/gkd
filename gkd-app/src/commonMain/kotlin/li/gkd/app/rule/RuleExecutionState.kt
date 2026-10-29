package li.gkd.app.rule

import kotlinx.atomicfu.atomic
import kotlinx.serialization.Serializable

@Serializable
enum class RuleDelayKind {
    Match,
    Action,
}

@Serializable
data class RuleStateValues(val count: Int, val trigger: Long, val delay: Long, val changed: Long)

/** Mutable execution data, separate from the parsed rule definition and selectors. */
class RuleExecutionState {
    fun values() =
        RuleStateValues(
            actionCount.value,
            actionTriggerTime.value,
            actionDelayTriggerTime.value,
            matchChangedTime.value,
        )

    var actionCount = atomic(0)
        private set

    var actionTriggerTime = atomic(0L)
        private set

    val actionDelayTriggerTime = atomic(0L)
    val matchChangedTime = atomic(0L)

    fun shareCount(other: RuleExecutionState) {
        actionCount = other.actionCount
    }

    fun shareCooldown(other: RuleExecutionState) {
        actionTriggerTime = other.actionTriggerTime
    }

    fun transition(operation: RuleTransition, time: Long) {
        when (operation) {
            RuleTransition.Delay -> actionDelayTriggerTime.value = time
            RuleTransition.Trigger -> {
                actionTriggerTime.value = time
                actionDelayTriggerTime.value = 0
                actionCount.incrementAndGet()
            }
            RuleTransition.Reset -> {
                actionCount.value = 0
                actionDelayTriggerTime.value = 0
                actionTriggerTime.value = 0
                matchChangedTime.value = time
            }
        }
    }
}
