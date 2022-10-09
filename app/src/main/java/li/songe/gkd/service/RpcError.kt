package li.songe.gkd.service
import kotlinx.serialization.Serializable

@Serializable
data class RpcError(
    val message: String = "unknown error",
    val code: Int = 0,
)
