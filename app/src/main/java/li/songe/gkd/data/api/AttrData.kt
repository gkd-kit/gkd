package li.songe.gkd.data.api

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class AttrData(
    @Json(name = "id")
    val id: String?,
    @Json(name = "className")
    val className: String?,
    @Json(name = "childCount")
    val childCount: Int,
    @Json(name = "text")
    val text: String?,
    @Json(name = "isClickable")
    val isClickable: Boolean,
    @Json(name = "contentDescription")
    val contentDescription: String?,
    @Json(name = "left")
    val left: Int,
    @Json(name = "top")
    val top: Int,
    @Json(name = "right")
    val right: Int,
    @Json(name = "bottom")
    val bottom: Int,
) {
    companion object {
        private val rect = Rect()
        fun info2data(
            nodeInfo: AccessibilityNodeInfo,
        ): AttrData {
            nodeInfo.getBoundsInScreen(rect)
            return AttrData(
                id = nodeInfo.viewIdResourceName,
                className = nodeInfo.className?.toString(),
                childCount = nodeInfo.childCount,
                text = nodeInfo.text?.toString(),
                isClickable = nodeInfo.isClickable,
                contentDescription = nodeInfo.contentDescription?.toString(),
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom
            )
        }
    }
}
