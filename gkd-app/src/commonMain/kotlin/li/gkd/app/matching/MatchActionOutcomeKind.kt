package li.gkd.app.matching

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchActionOutcomeKind {
    @SerialName("returned") Returned,
    @SerialName("cancelled") Cancelled,
    @SerialName("failed") Failed,
}
