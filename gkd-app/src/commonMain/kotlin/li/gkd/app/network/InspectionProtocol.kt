package li.gkd.app.network

import kotlinx.serialization.Serializable
import li.gkd.app.model.AppInfo
import li.gkd.app.model.DeviceInfo

@Serializable
data class RpcOk(
    val message: String? = null,
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

