package li.gkd.app.network.inspection

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.model.AppInfo
import li.gkd.app.model.DeviceInfo

val rpcSuccess = JsonObject(
    mapOf("message" to JsonPrimitive("success"))
)

@Serializable
data class RpcError(
    val code: String,
    val message: String,
    val stackTrace: String? = null,
    val path: String? = null,
)

@Serializable
data class ReqId(
    val id: Long,
)

@Serializable
data class ServerInfo(
    val device: DeviceInfo,
    val gkdAppInfo: AppInfo
)
