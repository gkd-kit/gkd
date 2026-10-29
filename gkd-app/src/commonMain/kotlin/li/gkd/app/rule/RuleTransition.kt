package li.gkd.app.rule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RuleTransition {
    @SerialName("delay") Delay,
    @SerialName("trigger") Trigger,
    @SerialName("reset") Reset;

    val recordOperation: RuleStateOperation
        get() =
            when (this) {
                Delay -> RuleStateOperation.Delay
                Trigger -> RuleStateOperation.Trigger
                Reset -> RuleStateOperation.Reset
            }
}
