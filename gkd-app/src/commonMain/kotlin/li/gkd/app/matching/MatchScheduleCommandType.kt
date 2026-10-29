package li.gkd.app.matching

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchScheduleCommandType {
    @SerialName("attach") Attach,
    @SerialName("detach") Detach,
    @SerialName("invalidate") Invalidate,
    @SerialName("close") Close,
    @SerialName("policy") Policy,
    @SerialName("request") Request,
    @SerialName("execute") Execute,
    @SerialName("follow_up") FollowUp,
    @SerialName("rule_delay") RuleDelay,
    @SerialName("cancel_delays") CancelDelays,
    @SerialName("wake") Wake,
    @SerialName("rules") Rules,
    @SerialName("track") Track,
    @SerialName("select") Select,
    @SerialName("cancel") Cancel,
    @SerialName("query_start") QueryStart,
    @SerialName("query_end") QueryEnd,
    @SerialName("action_start") ActionStart,
    @SerialName("action_end") ActionEnd,
    @SerialName("finish") Finish,
    @SerialName("commit") Commit,
}
