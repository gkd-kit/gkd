package li.songe.gkd.debug.server.api


import kotlinx.serialization.Serializable
import li.songe.gkd.accessibility.GkdAbService

@Serializable
data class Window(
    val appId: String? = null,
    val activityId: String? = null,
    val nodes: List<Node>? = null,
) {
    companion object {
        val singleton: Window
            get()  {
                val shot = GkdAbService.currentNodeSnapshot()
              return  Window(
                    appId = shot?.appId,
                    activityId = shot?.activityId,
                    nodes = Node.info2nodeList(shot?.root)
                )
            }
    }
}
