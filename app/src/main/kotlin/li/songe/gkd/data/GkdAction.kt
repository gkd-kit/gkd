package li.songe.gkd.data

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.service.A11yService
import li.songe.gkd.shizuku.casted
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.util.ScreenUtils

@Serializable
data class GkdAction(
    val selector: String,
    val fastQuery: Boolean = false,
    val action: String? = null,
    override val position: RawSubscription.Position? = null,
    override val swipeArg: RawSubscription.SwipeArg? = null,
) : RawSubscription.LocationProps

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
    val shell: Boolean = false,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    abstract suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.casted.boundsInScreen
            val p = locationProps.position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            if (!ScreenUtils.inScreen(x, y)) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = x to y,
                )
            }
            return ActionResult(
                action = action,
                result = if (shizukuContextFlow.value.tap(x, y)) {
                    true
                } else {
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, ViewConfiguration.getTapTimeout().toLong()
                        )
                    )
                    A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null
                },
                position = x to y
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(node, locationProps)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(node, locationProps)
        }
    }

    data object LongClickNode : ActionPerformer("longClickNode") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK).apply {
                    if (this) {
                        delay(LongClickCenter.LONG_DURATION)
                    }
                }
            )
        }
    }

    data object LongClickCenter : ActionPerformer("longClickCenter") {
        const val LONG_DURATION = 500L
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.casted.boundsInScreen
            val p = locationProps.position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            // 某些系统的 ViewConfiguration.getLongPressTimeout() 返回 300 , 这将导致触发普通的 click 事件
            if (!ScreenUtils.inScreen(x, y)) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = x to y,
                )
            }
            return ActionResult(
                action = action,
                result = if (shizukuContextFlow.value.tap(x, y, LONG_DURATION)) {
                    true
                } else {
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, LONG_DURATION
                        )
                    )
                    (A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null).apply {
                        if (this) {
                            delay(LONG_DURATION)
                        }
                    }
                },
                position = x to y
            )
        }
    }

    data object LongClick : ActionPerformer("longClick") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            if (node.isLongClickable) {
                val result = LongClickNode.perform(node, locationProps)
                if (result.result) {
                    return result
                }
            }
            return LongClickCenter.perform(node, locationProps)
        }
    }

    data object Back : ActionPerformer("back") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = A11yRuleEngine.performActionBack()
            )
        }
    }

    data object None : ActionPerformer("none") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = true
            )
        }
    }

    data object Swipe : ActionPerformer("swipe") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.casted.boundsInScreen
            val swipeArg = locationProps.swipeArg ?: return None.perform(node, locationProps)
            val startP = swipeArg.start.calc(rect)
            val endP = swipeArg.end?.calc(rect) ?: startP
            if (startP == null || endP == null) {
                return None.perform(node, locationProps)
            }
            val startX = startP.first
            val startY = startP.second
            val endX = endP.first
            val endY = endP.second
            if (!(ScreenUtils.inScreen(startX, startY) && ScreenUtils.inScreen(endX, endY))) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = endX to endY,
                )
            }
            return if (shizukuContextFlow.value.swipe(
                    startX,
                    startY,
                    endX,
                    endY,
                    swipeArg.duration
                )
            ) {
                ActionResult(
                    action = action,
                    result = true,
                    shell = true,
                    position = endX to endY,
                )
            } else {
                val gestureDescription = GestureDescription.Builder()
                val path = Path()
                path.moveTo(startX, startY)
                path.lineTo(endX, endY)
                gestureDescription.addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, swipeArg.duration
                    )
                )
                ActionResult(
                    action = action,
                    result = (A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null).apply {
                        if (this) {
                            delay(swipeArg.duration)
                        }
                    },
                    position = endX to endY,
                )
            }
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
                None,
                Swipe,
            )
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
