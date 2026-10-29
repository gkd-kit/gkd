package li.gkd.app.matching.diagnostics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchDebugRunPhase {
    @SerialName("running") Running,
    @SerialName("cancelling") Cancelling,
    @SerialName("completed") Completed,
    @SerialName("cancelled") Cancelled,
    @SerialName("failed") Failed,
}
