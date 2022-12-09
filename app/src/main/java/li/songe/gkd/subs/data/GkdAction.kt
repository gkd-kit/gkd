package li.songe.gkd.subs.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GkdAction(
    @SerialName("type")
    val type: String,
    @SerialName("target")
    val target: String,
    @SerialName("position")
    val position: String?
)
