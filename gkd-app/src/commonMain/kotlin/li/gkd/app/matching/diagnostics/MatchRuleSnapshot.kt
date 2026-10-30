package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.RuleAvailability
import li.gkd.app.rule.ResolvedGlobalGroup
import li.gkd.app.rule.RuleSummary

@Serializable
data class MatchRuleIndex(
    val subscriptionId: Long,
    val subscriptionVersion: Int,
    val rules: List<String>,
)

fun RuleSummary.diagnosticIndex(): List<MatchRuleIndex> =
    (globalRules + appIdToRules.values.flatten())
        .groupBy { it.g.subscription }
        .map { (subscription, rules) ->
            MatchRuleIndex(subscription.id, subscription.version, rules.map { it.matchIdentity() })
        }

/**
 * Candidates explain filtering; definitions cover selected rules and their execution dependencies.
 */
@Serializable
data class MatchRuleSnapshot(
    val id: Long,
    val appId: String,
    val activityId: String?,
    val blockMatch: Boolean,
    val availability: RuleAvailability,
    val candidates: List<String>,
    val selected: List<String>,
    val definitions: List<Long>,
)

@Serializable
data class MatchRuleDefinition(
    val rule: String,
    val subscriptionVersion: Int,
    val group: JsonObject,
    val definition: JsonObject,
    val effective: JsonObject,
    val exclude: String?,
    val matchAnyApp: Boolean?,
    val excludedAppIds: Set<String>,
    val dependencies: List<String>,
)

fun ForegroundRuleSnapshot.diagnosticSnapshot(
    id: Long,
    register: (MatchRuleDefinition) -> Long,
): MatchRuleSnapshot {
    val definitions = currentRules.toMutableSet()
    val pending = ArrayDeque(currentRules)
    while (pending.isNotEmpty()) {
        pending.removeFirst().executionDependencies().forEach {
            if (definitions.add(it)) pending.addLast(it)
        }
    }
    return MatchRuleSnapshot(
        id,
        topActivity.appId,
        topActivity.activityId,
        blockMatch,
        ruleAvailability,
        (appRules + ruleSummary.globalRules).map { it.matchIdentity() },
        currentRules.map { it.matchIdentity() },
        definitions.map { rule ->
            val group = rule.g
            val raw = group.group.cacheJsonObject
            val parameters = rule.parameters
            register(
                MatchRuleDefinition(
                    rule.matchIdentity(),
                    group.subscription.version,
                    JsonObject(raw.filterKeys { it != "rules" && it !in diagnosticExampleFields }),
                    JsonObject(
                        raw.getValue("rules").jsonArray[rule.index].jsonObject.filterKeys {
                            it !in diagnosticExampleFields
                        },
                    ),
                    buildJsonObject {
                        put("actionCd", parameters.actionCd)
                        put(
                            "actionMaximum",
                            parameters.actionMaximum?.let(::JsonPrimitive) ?: JsonNull,
                        )
                        put("matchDelay", parameters.matchDelay)
                        put("actionDelay", parameters.actionDelay)
                        put("matchTime", parameters.matchTime?.let(::JsonPrimitive) ?: JsonNull)
                        put("forcedTime", parameters.forcedTime)
                        put("resetMatch", parameters.resetMatchType.value)
                        put("fastQuery", parameters.matchOptions.fastQuery)
                        put("matchRoot", parameters.matchRoot)
                        put("order", parameters.order)
                        put("priorityTime", parameters.priorityTime)
                        put("priorityActionMaximum", parameters.priorityActionMaximum)
                    },
                    group.config?.exclude,
                    (group as? ResolvedGlobalGroup)?.matchAnyApp,
                    (group as? ResolvedGlobalGroup)?.groupExcludeAppIds ?: emptySet(),
                    rule.executionDependencies().map { it.matchIdentity() },
                ),
            )
        },
    )
}

private val diagnosticExampleFields = setOf("snapshotUrls", "excludeSnapshotUrls", "exampleUrls")

@Serializable data class MatchEntrySummary(val source: String, val reason: String, val count: Long)

@Serializable
data class MatchDiagnosticCapture(
    val session: MatchDiagnosticState,
    val events: List<MatchTrace>,
    val ruleSnapshots: List<MatchRuleSnapshot>,
    val droppedRuleSnapshots: Long,
    val entries: List<MatchEntrySummary>,
    val scope: String =
        "foreground_candidate_ids_and_selected_definitions; optional_recorded_selector_access; optional_scheduler_rules_action_lifecycle; observed_evidence_only",
    val queryRecordings: List<MatchQueryRecording> = emptyList(),
    val droppedQueryRecordings: Long = 0,
    val systemRecording: MatchSystemRecording? = null,
    val initialRules: List<MatchRuleIndex>? = null,
    val ruleDefinitions: Map<Long, MatchRuleDefinition> = emptyMap(),
    val operationStatistics: MatchOperationStatistics? = null,
    val runs: List<MatchDebugRunState> = emptyList(),
    val runtime: MatchDebugSnapshot? = null,
    val runtimeCapturedAt: Long? = null,
    val appId: String,
    val appVersion: String,
    val platform: String,
    val keyEvents: List<MatchTrace> = emptyList(),
    val droppedKeyEvents: Long = 0,
    val boundaryEvents: List<MatchTrace> = emptyList(),
    val droppedBoundaryEvents: Long = 0,
)
