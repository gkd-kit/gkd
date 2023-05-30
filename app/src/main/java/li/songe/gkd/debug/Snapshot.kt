package li.songe.gkd.debug

import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.accessibility.GkdAbService
import li.songe.gkd.util.Ext

@Serializable
data class Snapshot(
    val id: Long = System.currentTimeMillis(),
    val device: DeviceSnapshot? = DeviceSnapshot.instance,
    val screenHeight: Int = ScreenUtils.getScreenHeight(),
    val screenWidth: Int = ScreenUtils.getScreenWidth(),
    val appId: String? = null,
    val appName: String? = null,
    val activityId: String? = null,
    val nodes: List<NodeSnapshot>? = null,
) {
    companion object {
        fun current(): Snapshot {
            val shot = GkdAbService.currentNodeSnapshot()
            return Snapshot(
                appId = shot?.appId,
                appName = if (shot?.appId != null) {
                    Ext.getAppName(shot.appId)
                } else null,
                activityId = shot?.activityId,
                nodes = NodeSnapshot.info2nodeList(shot?.root),
            )
        }
    }
}
