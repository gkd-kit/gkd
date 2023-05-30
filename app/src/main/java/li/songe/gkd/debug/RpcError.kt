package li.songe.gkd.debug

import kotlinx.serialization.Serializable

@Serializable
data class RpcError(
    override val message: String = "unknown error",
    val code: Int = 0,
    val X_Rpc_Result: Boolean = true
) : Exception(message) {
    companion object {
        const val HeaderKey = "X_Rpc_Result"
        const val HeaderOkValue = "ok"
        const val HeaderErrorValue = "error"
    }
}
