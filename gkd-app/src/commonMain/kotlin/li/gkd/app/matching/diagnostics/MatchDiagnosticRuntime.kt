package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import li.gkd.app.matching.MatchEngineSnapshot
import li.gkd.app.matching.input.ForegroundDecision

@Serializable
data class ForegroundDebugState(
    val interactive: Boolean,
    val locked: Boolean,
    val serviceMode: Int?,
    val foreground: ForegroundDecision,
    val serviceEffective: Boolean,
)

@Serializable
data class MatchDebugSnapshot(
    val engine: MatchEngineSnapshot,
    val foreground: ForegroundDebugState?,
)

/** Cached foreground observations; not an atomic OS snapshot. */
expect fun matchForegroundSnapshot(): ForegroundDebugState?
