package li.gkd.app.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeInfo(
    val id: Int,
    val pid: Int,
    val idQf: Boolean?,
    val textQf: Boolean?,
    val attr: AttrInfo,
)

