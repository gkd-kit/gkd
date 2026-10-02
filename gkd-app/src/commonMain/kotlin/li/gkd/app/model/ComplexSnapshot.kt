package li.gkd.app.model

import kotlinx.serialization.Serializable
import li.gkd.db.BaseSnapshot
import li.gkd.db.Snapshot

@Serializable
data class ComplexSnapshot(
    override val id: Long,
    override val appId: String,
    override val activityId: String?,
    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,
    val appInfo: AppInfo?,
    val gkdAppInfo: AppInfo?,
    val device: DeviceInfo,
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
