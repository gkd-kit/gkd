package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import li.gkd.app.settings.AutomatorMode

@Serializable
enum class MatchNodeSource {
    Root,
    Event,
    Refresh,
}

@Serializable
enum class MatchNodeReadReason {
    Event,
    SourceReused,
    MissingOrExpired,
    ForegroundConfirmation,
}

@Serializable
enum class MatchNodeRole {
    Source,
    Root,
}

@Serializable
enum class MatchFastQueryKind {
    Text,
    Id,
    Vid,
}

@Serializable
enum class MatchServiceEvent {
    Connected,
    QueryStarted,
    HandoffShutdownRequested,
    Disconnected,
}

@Serializable
data class MatchServiceConnection(
    val event: MatchServiceEvent,
    val service: AutomatorMode,
    val connectionId: Long,
    val targetConnectionId: Long? = null,
)

/** Values copied from a completed read; never retains an Android node or service. */
@Serializable
data class MatchNodeProvenance(
    val readId: Long,
    val connectionId: Long,
    val service: AutomatorMode,
    val source: MatchNodeSource,
    val reason: MatchNodeReadReason,
    val page: Int,
    val appId: String,
    val observedActivityId: String?,
    val windowId: Int,
    val acquiredAtMillis: Long,
    val ageMillis: Long = 0,
    val parentReadId: Long? = null,
    val eventId: Long? = null,
    val summary: MatchNodeSummary? = null,
)

/** Local node fields only; no refresh, parent lookup, or tree traversal. */
@Serializable
data class MatchNodeSummary(
    val className: String?,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val childCount: Int,
)

@Serializable data class MatchEventBatch(val eventIds: List<Long>, val selectedEventId: Long?)

/** Bound diagnostic text without retaining platform CharSequences. */
fun CharSequence?.diagnosticText(): String? = this?.take(256)?.toString()

@Serializable
data class MatchFastQueryResult(
    val kind: MatchFastQueryKind,
    val value: String,
    val count: Int,
    val anomalousCandidates: List<MatchFastQueryCandidate> = emptyList(),
)

@Serializable
data class MatchFastQueryCandidate(
    val className: String?,
    val viewId: String?,
    val contentDescription: String?,
    val windowId: Int,
    val visibleToUser: Boolean,
)

@Serializable
data class MatchPageObservation(
    val page: Int,
    val appId: String,
    val activityId: String?,
    val firstConfirmation: Boolean? = null,
)

@Serializable
data class MatchWindowEvent(
    val eventType: Int,
    val windowId: Int,
    val contentChangeTypes: Int,
    val windowChanges: Int?,
    val sourceTimeMillis: Long,
    val appId: String?,
    val className: String?,
    val eventId: Long? = null,
    val text: List<String> = emptyList(),
    val contentDescription: String? = null,
)

@Serializable
data class MatchActionTarget(
    val windowId: Int,
    val appId: String?,
    val viewId: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val visibleToUser: Boolean,
    // Cached node text at execution, not a refreshed screen observation.
    val text: String? = null,
)

@Serializable
data class MatchTaskObservation(
    val requestId: Long,
    val callback: String,
    val appId: String? = null,
    val activityId: String? = null,
)

/** The key timeline intentionally omits per-property comparisons and low-level calls. */
fun MatchTrace.isKeyTrace(): Boolean =
    condition == null &&
        (windowEvent != null ||
            eventBatch != null ||
            actionTarget != null ||
            taskObservation != null ||
            node != null ||
            nodeRole != null ||
            serviceConnection != null ||
            pageObservation != null ||
            fastQuery != null ||
            operation?.name in keyOperationNames ||
            (operation == null && (stage != MatchStage.Idle || entrySource in keyEntrySources)))

private val keyOperationNames =
    setOf("Query", "Action", "RootWait", "SourceWait", "NodeRead:Root", "NodeRead:Source")
private val keyEntrySources = setOf("root_read", "source_read")
