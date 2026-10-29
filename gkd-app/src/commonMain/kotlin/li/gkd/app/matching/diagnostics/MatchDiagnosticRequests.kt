package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo

@Serializable
data class MatchSimulatedNode(
    val name: String,
    val attributes: Map<String, JsonPrimitive> = emptyMap(),
    val children: List<MatchSimulatedNode> = emptyList(),
)

@Serializable
data class MatchSimulation(
    val app: AppInfo,
    val activityId: String? = null,
    val root: MatchSimulatedNode,
    val rootDelayMillis: Long = 0,
    val actionMillis: Long = 0,
    val cleanupMillis: Long = 0,
)

@Serializable
data class MatchDebugRunRequest(
    val requestId: String,
    val subscription: String,
    val rule: String,
    val execute: Boolean = false,
    val simulation: MatchSimulation? = null,
)

@Serializable
data class MatchDebugResult(
    val matched: Boolean,
    val reason: String,
    val action: ActionResult? = null,
    val simulation: Boolean = false,
)

@Serializable
data class MatchDebugRunState(
    val request: MatchDebugRunRequest,
    val phase: MatchDebugRunPhase,
    val result: MatchDebugResult? = null,
    val error: String? = null,
    val engineRunId: Long? = null,
)
