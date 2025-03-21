package li.songe.gkd.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.shizuku.safeLongTap
import li.songe.gkd.shizuku.safeTap

@Serializable
data class GkdAction(
    val selector: String,
    val fastQuery: Boolean = false,
    val action: String? = null,
    val position: RawSubscription.Position? = null,
)

@Serializable
data class ActionResult(
    val action: String?,
    val result: Boolean,
    val shizuku: Boolean = false,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    abstract fun perform(
        context: AccessibilityService,
        node: AccessibilityNodeInfo,
        position: RawSubscription.Position?,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override fun perform(
            context: AccessibilityService,
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
            context: AccessibilityService,
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
                // TODO 在分屏/小窗模式下会点击到应用界面外部导致误触其它应用
                result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
                    val result = safeTap(x, y)
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
                    context.dispatchGesture(gestureDescription.build(), null, null)
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
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(context, node, position)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(context, node, position)
        }
    }

    data object LongClickNode : ActionPerformer("longClickNode") {
        override fun perform(
            context: AccessibilityService,
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
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val p = position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            // 500 https://cs.android.com/android/platform/superproject/+/android-8.1.0_r81:frameworks/base/core/java/android/view/ViewConfiguration.java;l=65
            // 400 https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/view/ViewConfiguration.java;drc=8b948e548b782592ae280a3cd9a91798afe6df9d;l=82
            // 某些系统的 ViewConfiguration.getLongPressTimeout() 返回 300 , 这将导致触发普通的 click 事件
            val longClickDuration = 500L
            return ActionResult(
                action = action,
                result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
                    val result = safeLongTap(x, y, longClickDuration)
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
                    // TODO 传入处理 callback
                    context.dispatchGesture(gestureDescription.build(), null, null)
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
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            if (node.isLongClickable) {
                val result = LongClickNode.perform(context, node, position)
                if (result.result) {
                    return result
                }
            }
            return LongClickCenter.perform(context, node, position)
        }
    }

    data object Back : ActionPerformer("back") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            )
        }
    }

    companion object {
        private val allSubObjects by lazy {
            arrayOf(ClickNode, ClickCenter, Click, LongClickNode, LongClickCenter, LongClick, Back)
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
