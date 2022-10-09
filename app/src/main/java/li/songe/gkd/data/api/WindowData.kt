package li.songe.gkd.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import li.songe.gkd.service.GkdAccessService
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@JsonClass(generateAdapter = true)
data class WindowData(
    @Json(name = "packageName")
    val packageName: String?=null,
    @Json(name = "activityName")
    val activityName: String?=null,
    @Json(name = "nodeDataList")
    val nodeDataList: List<NodeData>?=null,
) {
    companion object {
        val singleton:WindowData
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
            val moshi: Moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return moshi.adapter(WindowData::class.java).toJson(window)
        }
    }
}
