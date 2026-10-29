package li.gkd.app.matching

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchScheduleEffectType {
    @SerialName("start") Start,
    @SerialName("cancel") Cancel,
    @SerialName("arm") Arm,
    @SerialName("reject") Reject,
    @SerialName("commit") Commit,
}
