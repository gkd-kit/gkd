package li.gkd.app.matching.action

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import li.gkd.app.data.calc
import li.gkd.app.matching.diagnostics.MatchActionOperation
import li.gkd.app.matching.diagnostics.MatchActionTarget
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.diagnosticText
import li.gkd.app.matching.diagnostics.observeActionOperation
import li.gkd.app.model.ActionResult
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.toHidden
import li.gkd.app.service.A11yService
import li.gkd.app.service.TrackService
import li.gkd.app.subscription.PositionCalculationException
import li.gkd.app.subscription.PositionError
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.util.ScreenUtils

sealed class ActionPerformer(val action: String) {
    suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
        captureInputs: Boolean = false,
    ): ActionResult =
        try {
            currentCoroutineContext()[MatchActionOperation]?.let { context ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                context.diagnostics.record(
                    context.operation.runId,
                    MatchStage.Action,
                    "action_target",
                    rule = context.operation.rule,
                    sessionId = context.operation.sessionId,
                    actionTarget =
                        MatchActionTarget(
                            node.windowId,
                            node.packageName?.toString(),
                            node.viewIdResourceName,
                            bounds.left,
                            bounds.top,
                            bounds.right,
                            bounds.bottom,
                            node.isVisibleToUser,
                            text = node.text.diagnosticText(),
                        ),
                )
            }
            performAction(node, locationProps, captureInputs)
        } catch (error: PositionCalculationException) {
            ActionResult(action, false, coordinateError = error.error)
        }

    protected abstract suspend fun performAction(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
        captureInputs: Boolean,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            TrackService.addA11yNodePosition(node)
            return ActionResult(
                action = action,
                result =
                    observeActionOperation("AccessibilityNodeInfo.performAction:CLICK") {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    },
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            val position = calculateActionPosition(node, locationProps, captureInputs)
            val (x, y) = position
            coordinateBoundsFailure(action, position, "position")?.let {
                return it
            }
            TrackService.addXyPosition(x, y)
            return ActionResult(
                action = action,
                result =
                    privilegeContextFlow.value?.let { privilege ->
                        observeActionOperation("PrivilegedTap") { privilege.tap(x, y) }
                    }
                        ?: run {
                            val gestureDescription = GestureDescription.Builder()
                            val path = Path()
                            path.moveTo(x, y)
                            gestureDescription.addStroke(
                                GestureDescription.StrokeDescription(
                                    path,
                                    0,
                                    ViewConfiguration.getTapTimeout().toLong(),
                                ),
                            )
                            A11yService.instance?.awaitGesture(gestureDescription.build()) == true
                        },
                position = x to y,
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(node, locationProps, captureInputs)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(node, locationProps, captureInputs)
        }
    }

    data object LongClickNode : ActionPerformer("longClickNode") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            TrackService.addA11yNodePosition(node)
            return ActionResult(
                action = action,
                result =
                    observeActionOperation("AccessibilityNodeInfo.performAction:LONG_CLICK") {
                            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        }
                        .apply {
                            if (this) {
                                delay(LongClickCenter.LONG_DURATION.milliseconds)
                            }
                        },
            )
        }
    }

    data object LongClickCenter : ActionPerformer("longClickCenter") {
        const val LONG_DURATION = 500L

        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            val position = calculateActionPosition(node, locationProps, captureInputs)
            val (x, y) = position
            // 某些系统的 ViewConfiguration.getLongPressTimeout() 返回 300 , 这将导致触发普通的 click 事件
            coordinateBoundsFailure(action, position, "position")?.let {
                return it
            }
            TrackService.addXyPosition(x, y)
            return ActionResult(
                action = action,
                result =
                    privilegeContextFlow.value?.let { privilege ->
                        observeActionOperation("PrivilegedLongPress") {
                            privilege.tap(x, y, LONG_DURATION)
                        }
                    }
                        ?: run {
                            A11yService.instance?.awaitSwipe(x, y, x, y, LONG_DURATION) == true
                        },
                position = x to y,
            )
        }
    }

    data object LongClick : ActionPerformer("longClick") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            if (node.isLongClickable) {
                val result = LongClickNode.perform(node, locationProps, captureInputs)
                if (result.result) {
                    return result
                }
            }
            return LongClickCenter.perform(node, locationProps, captureInputs)
        }
    }

    data object Back : ActionPerformer("back") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = performActionBack(),
            )
        }
    }

    data object None : ActionPerformer("none") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = true,
            )
        }
    }

    data object Swipe : ActionPerformer("swipe") {
        override suspend fun performAction(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
            captureInputs: Boolean,
        ): ActionResult {
            val rect = node.toHidden.boundsInScreen
            val swipeArg =
                locationProps.swipeArg
                    ?: return ActionResult(
                        action = action,
                        result = false,
                    )
            val screenSize = ScreenUtils.getSize()
            val startP = swipeArg.start.calc(rect, "swipeArg.start", captureInputs, screenSize)
            val endP = swipeArg.end?.calc(rect, "swipeArg.end", captureInputs, screenSize) ?: startP
            val startX = startP.first
            val startY = startP.second
            val endX = endP.first
            val endY = endP.second
            coordinateBoundsFailure(action, startP, "swipeArg.start", endP)?.let {
                return it
            }
            coordinateBoundsFailure(action, endP, "swipeArg.end")?.let {
                return it
            }
            TrackService.addSwipePosition(startX, startY, endX, endY, swipeArg.duration)
            val injected =
                privilegeContextFlow.value?.let { privilege ->
                    observeActionOperation("PrivilegedSwipe") {
                        privilege.swipe(startX, startY, endX, endY, swipeArg.duration)
                    }
                }
            return if (injected != null) {
                ActionResult(
                    action = action,
                    result = injected,
                    shell = true,
                    position = endX to endY,
                )
            } else {
                ActionResult(
                    action = action,
                    result =
                        A11yService.instance?.awaitSwipe(
                            startX,
                            startY,
                            endX,
                            endY,
                            swipeArg.duration,
                        ) == true,
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

private fun calculateActionPosition(
    node: AccessibilityNodeInfo,
    locationProps: RawSubscription.LocationProps,
    captureInputs: Boolean,
): Pair<Float, Float> {
    val rect = node.toHidden.boundsInScreen
    return locationProps.position?.calc(rect, captureInputs = captureInputs)
        ?: (((rect.right + rect.left) / 2f) to ((rect.bottom + rect.top) / 2f))
}

private fun coordinateBoundsFailure(
    action: String,
    position: Pair<Float, Float>,
    field: String,
    resultPosition: Pair<Float, Float> = position,
): ActionResult? =
    if (ScreenUtils.contains(position.first, position.second)) null
    else
        ActionResult(
            action = action,
            result = false,
            position = resultPosition,
            coordinateError = PositionError(field, "COORDINATE_OUT_OF_BOUNDS"),
        )
