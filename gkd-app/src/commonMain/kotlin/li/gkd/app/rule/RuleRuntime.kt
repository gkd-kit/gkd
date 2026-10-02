package li.gkd.app.rule

import kotlinx.atomicfu.atomic

data class RuleMatchEnvironment(
    val launcherAppId: String = "",
    val systemAppIds: Set<String> = emptySet(),
)

/** Owned by the execution host; shared across its rule snapshots, never a global service. */
class RuleRuntime(
    val now: () -> Long = System::currentTimeMillis,
    val environment: () -> RuleMatchEnvironment = { RuleMatchEnvironment() },
    private val cancelPending: (ResolvedRule) -> Unit = {},
) {
    private data class Trigger(val rule: ResolvedRule?, val time: Long)

    private val trigger = atomic(Trigger(null, 0L))
    private val changedAt = atomic(0L)
    val lastTriggerRule: ResolvedRule? get() = trigger.value.rule
    val lastTriggerTime: Long get() = trigger.value.time
    val appChangeTime: Long get() = changedAt.value

    fun onAppChanged(time: Long) {
        changedAt.value = time
    }

    fun onTriggered(rule: ResolvedRule, time: Long) {
        trigger.value = Trigger(rule, time)
    }

    fun onReset(rule: ResolvedRule) = cancelPending(rule)
}
