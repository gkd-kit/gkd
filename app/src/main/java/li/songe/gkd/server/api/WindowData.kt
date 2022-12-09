package li.songe.gkd.server.api


import li.songe.gkd.accessibility.GkdAccessService
import kotlinx.serialization.Serializable

@Serializable
data class WindowData(
    val packageName: String?=null,
    val activityName: String?=null,
    val nodeDataList: List<NodeData>?=null,
) {
    companion object {
        val singleton: WindowData
        get() = WindowData(
            packageName = GkdAccessService.currentPackageName(),
            activityName = GkdAccessService.currentActivityName(),
            nodeDataList = NodeData.info2nodeList(GkdAccessService.rootInActiveWindow())
        )

        fun stringify(): String {
            val window = WindowData(
                packageName = GkdAccessService.currentPackageName(),
                activityName = GkdAccessService.currentActivityName(),
                nodeDataList = NodeData.info2nodeList(GkdAccessService.rootInActiveWindow())
            )
            return ""
        }
    }
}
