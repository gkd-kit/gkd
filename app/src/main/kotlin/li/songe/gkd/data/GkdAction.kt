package li.songe.gkd.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable


typealias ActionFc = (context: AccessibilityService, node: AccessibilityNodeInfo) -> ActionResult


@Serializable
data class GkdAction(
    val selector: String,
    val quickFind: Boolean = false,
    val action: String? = null,
)

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
)

val clickNode: ActionFc = { _, node ->
    ActionResult(
        action = "clickNode", result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    )
}

val clickCenter: ActionFc = { context, node ->
    val react = Rect()
    node.getBoundsInScreen(react)
    val x = (react.right + react.left) / 2f
    val y = (react.bottom + react.top) / 2f
    ActionResult(
        action = "clickCenter",
        result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, ViewConfiguration.getTapTimeout().toLong()
                )
            )
            context.dispatchGesture(gestureDescription.build(), null, null)
            true
        } else {
            false
        }
    )
}

val click: ActionFc = { context, node ->
    if (node.isClickable) clickNode(context, node) else clickCenter(context, node)
}

val longClickNode: ActionFc = { _, node ->
    ActionResult(
        action = "longClickNode",
        result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    )
}

val longClickCenter: ActionFc = { context, node ->
    val react = Rect()
    node.getBoundsInScreen(react)
    val x = (react.right + react.left) / 2f
    val y = (react.bottom + react.top) / 2f
    // 内部的 DEFAULT_LONG_PRESS_TIMEOUT 常量是 400
    // 而 ViewConfiguration.getLongPressTimeout() 返回 300, 这将导致触发普通的 click 事件
    ActionResult(
        action = "longClickCenter",
        result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, 400L
                )
            )
            // TODO 传入处理 callback
            context.dispatchGesture(gestureDescription.build(), null, null)
            true
        } else {
            false
        }
    )
}


val longClick: ActionFc = { context, node ->
    if (node.isLongClickable) longClickNode(context, node) else longClickCenter(context, node)
}

val backFc: ActionFc = { context, _ ->
    ActionResult(
        action = "back",
        result = context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    )
}

fun getActionFc(action: String?): ActionFc {
    return when (action) {
        "clickNode" -> clickNode
        "clickCenter" -> clickCenter
        "back" -> backFc
        "longClick" -> longClick
        "longClickNode" -> longClickNode
        "longClickCenter" -> longClickCenter
        else -> click
    }
}