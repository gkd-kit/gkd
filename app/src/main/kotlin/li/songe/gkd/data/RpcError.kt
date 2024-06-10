package li.songe.gkd.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RpcError(
    override val message: String,
    @SerialName("__error") val error: Boolean = true,
    val unknown: Boolean = false,
) : Exception(message)
