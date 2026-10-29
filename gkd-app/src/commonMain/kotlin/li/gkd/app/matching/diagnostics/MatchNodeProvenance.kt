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
    HandoffShutdownRequested,
}

@Serializable
data class MatchServiceConnection(
    val event: MatchServiceEvent,
    val service: AutomatorMode,
    val connectionId: Long,
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
    val activityId: String?,
    val windowId: Int,
    val acquiredAtMillis: Long,
    val ageMillis: Long = 0,
    val parentReadId: Long? = null,
)

@Serializable
data class MatchFastQueryResult(val kind: MatchFastQueryKind, val value: String, val count: Int)

@Serializable
data class MatchPageObservation(val page: Int, val appId: String, val activityId: String?)

/** The key timeline intentionally omits per-property comparisons and low-level calls. */
fun MatchTrace.isKeyTrace(): Boolean =
    condition == null &&
        (node != null ||
            nodeRole != null ||
            serviceConnection != null ||
            pageObservation != null ||
            fastQuery != null ||
            operation?.name in keyOperationNames ||
            (operation == null && (stage != MatchStage.Idle || entrySource in keyEntrySources)))

private val keyOperationNames =
    setOf("Query", "Action", "RootWait", "SourceWait", "NodeRead:Root", "NodeRead:Source")
private val keyEntrySources = setOf("root_read", "source_read")
