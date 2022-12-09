package li.songe.gkd.subs.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GkdRule(
    @SerialName("key")
    val key: Int,
    @SerialName("match")
    val match: String,
    @SerialName("desc")
    val desc: String? = null,
    @SerialName("cd")
    val cd: Int? = null,
    @SerialName("activityId")
    val activityId: String? = null,
)
