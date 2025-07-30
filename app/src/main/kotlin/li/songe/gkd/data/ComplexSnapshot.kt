package li.songe.gkd.data

import kotlinx.serialization.Serializable
import li.songe.gkd.util.getPkgInfo

@Serializable
data class ComplexSnapshot(
    override val id: Long,
    override val appId: String?,
    override val activityId: String?,
    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,
    val appInfo: AppInfo? = appId?.let { getPkgInfo(appId)?.toAppInfo() },
    val gkdAppInfo: AppInfo? = selfAppInfo,
    val device: DeviceInfo = DeviceInfo.instance,
    val nodes: List<NodeInfo>,
) : BaseSnapshot {
    fun toSnapshot(): Snapshot {
        return Snapshot(
            id = id,
            appId = appId,
            activityId = activityId,
            screenHeight = screenHeight,
            screenWidth = screenWidth,
            isLandscape = isLandscape,
        )
    }
}
