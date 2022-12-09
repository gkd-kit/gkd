package li.songe.gkd.subs.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GkdGroup(
    @SerialName("key")
    val key: Int,
    @SerialName("ruleList")
    val ruleList: List<GkdRule>,
    @SerialName("desc")
    val desc: String? = null,
    @SerialName("activityId")
    val activityId: String? = null,
    @SerialName("cd")
    val cd: Int? = null
)
