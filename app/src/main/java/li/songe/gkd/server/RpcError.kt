package li.songe.gkd.server

import kotlinx.serialization.Serializable

@Serializable
data class RpcError(
    val message: String = "unknown error",
    val code: Int = 0,
    val __error: Boolean = true
)
