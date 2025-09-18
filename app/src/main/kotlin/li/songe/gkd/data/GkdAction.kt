package li.songe.gkd.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.gkd.service.A11yService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.util.ScreenUtils

@Serializable
data class GkdAction(
    val selector: String,
    val fastQuery: Boolean = false,
    val action: String? = null,
    val position: RawSubscription.Position? = null,
)

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
    val shizuku: Boolean = false,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    val service: AccessibilityService
        get() = A11yService.instance!!

    abstract fun perform(
        node: AccessibilityNodeInfo,
        position: RawSubscription.Position?,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val p = position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            return ActionResult(
                action = action,
                result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
                    val result = shizukuContextFlow.value.serviceWrapper?.safeTap(x, y)
                    if (result != null) {
                        return ActionResult(action, result, true, position = x to y)
                    }
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, ViewConfiguration.getTapTimeout().toLong()
                        )
                    )
                    service.dispatchGesture(gestureDescription.build(), null, null)
                    true
                } else {
                    false
                },
                position = x to y
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(node, position)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(node, position)
        }
    }

    data object LongClickNode : ActionPerformer("longClickNode") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            )
        }
    }

    data object LongClickCenter : ActionPerformer("longClickCenter") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val p = position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            // 某些系统的 ViewConfiguration.getLongPressTimeout() 返回 300 , 这将导致触发普通的 click 事件
            val longClickDuration = 500L
            return ActionResult(
                action = action,
                result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
                    val result =
                        shizukuContextFlow.value.serviceWrapper?.safeTap(x, y, longClickDuration)
                    if (result != null) {
                        return ActionResult(action, result, true, position = x to y)
                    }
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, longClickDuration
                        )
                    )
                    service.dispatchGesture(gestureDescription.build(), null, null)
                    true
                } else {
                    false
                },
                position = x to y
            )
        }
    }

    data object LongClick : ActionPerformer("longClick") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            if (node.isLongClickable) {
                val result = LongClickNode.perform(node, position)
                if (result.result) {
                    return result
                }
            }
            return LongClickCenter.perform(node, position)
        }
    }

    data object Back : ActionPerformer("back") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            )
        }
    }

    data object None : ActionPerformer("none") {
        override fun perform(
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = true
            )
        }
    }

    companion object {
        private val allSubObjects by lazy {
            arrayOf(
                ClickNode,
                ClickCenter,
                Click,
                LongClickNode,
                LongClickCenter,
                LongClick,
                Back,
                None
            )
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
