package li.gkd.app.matching

import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleSummary

private fun RuleSummary.executionRules(): List<ResolvedRule> =
    globalRules + appIdToRules.values.flatten()

/** Called once when the execution host installs a new definition snapshot. */
fun RuleSummary.retainUnaffectedExecution(previous: RuleSummary) {
    val old = previous.executionRules().associateBy { it.matchIdentity() }
    val fresh = executionRules().associateBy { it.matchIdentity() }
    val affected =
        (old.keys + fresh.keys).filterTo(mutableSetOf()) { id ->
            val before = old[id]
            val after = fresh[id]
            before == null ||
                after == null ||
                !before.sameDefinition(after) ||
                before.executionDependencies().map { it.matchIdentity() }.toSet() !=
                    after.executionDependencies().map { it.matchIdentity() }.toSet()
        }
    // Edges are collected from both snapshots: removing an edge must invalidate its
    // former consumers too. Shared cells are bidirectional; prerequisites are directed.
    val consumers = mutableMapOf<String, MutableSet<String>>()
    for (rules in listOf(old.values, fresh.values)) {
        for (rule in rules) {
            val id = rule.matchIdentity()
            for (dependency in rule.executionDependencies()) {
                consumers.getOrPut(dependency.matchIdentity()) { mutableSetOf() }.add(id)
                if (rule.sharesState(dependency)) {
                    consumers.getOrPut(id) { mutableSetOf() }.add(dependency.matchIdentity())
                }
            }
        }
    }
    val pending = ArrayDeque(affected)
    while (pending.isNotEmpty()) {
        consumers[pending.removeFirst()].orEmpty().forEach {
            if (affected.add(it)) pending.addLast(it)
        }
    }
    fresh.forEach { (id, rule) -> if (id !in affected) rule.retainExecution(checkNotNull(old[id])) }
}
