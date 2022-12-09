package li.songe.gkd.subs.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GkdSubs(
    @SerialName("version")
    val version: Int,
    @SerialName("desc")
    val desc: String? = null,
    @SerialName("author")
    val author: String? = null,
    @SerialName("updateUrl")
    val updateUrl: String? = null,
    @SerialName("appList")
    val appList: List<GkdApp>
)
