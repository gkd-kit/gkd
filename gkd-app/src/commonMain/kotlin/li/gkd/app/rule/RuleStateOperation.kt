package li.gkd.app.rule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RuleStateOperation {
    @SerialName("delay") Delay,
    @SerialName("trigger") Trigger,
    @SerialName("reset") Reset,
    @SerialName("status") Status,
}
