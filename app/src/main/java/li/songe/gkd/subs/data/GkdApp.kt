package li.songe.gkd.subs.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GkdApp(
    @SerialName("id") val id: String,
    @SerialName("groupList") val groupList: List<GkdGroup>
)
