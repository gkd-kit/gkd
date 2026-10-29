package li.gkd.app.matching.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import li.gkd.app.matching.diagnostics.observeActionOperation

/**
 * The system owns a submitted gesture. Cancelling its caller cannot retract it: keep the input lane
 * occupied until the completion/cancellation callback arrives.
 */
suspend fun AccessibilityService.awaitGesture(gesture: GestureDescription): Boolean {
    currentCoroutineContext().ensureActive()
    val result = withContext(NonCancellable) { dispatchAndAwaitGesture(gesture) }
    currentCoroutineContext().ensureActive()
    return result == GestureSegmentResult.Completed
}

private suspend fun AccessibilityService.dispatchAndAwaitGesture(
    gesture: GestureDescription,
): GestureSegmentResult {
    val finished = CompletableDeferred<GestureSegmentResult>()
    val submitted =
        observeActionOperation("AccessibilityService.dispatchGesture") {
            dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        finished.complete(GestureSegmentResult.Completed)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        finished.complete(GestureSegmentResult.Cancelled)
                    }
                },
                null,
            )
        }
    if (!submitted) return GestureSegmentResult.Rejected
    return observeActionOperation("GestureCallbackWait") { finished.await() }
}

suspend fun AccessibilityService.awaitSwipe(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    durationMillis: Long,
): Boolean =
    continuousSwipe<GestureDescription.StrokeDescription>(
        startX,
        startY,
        endX,
        endY,
        durationMillis,
        create = { previous, segment ->
            val path =
                Path().apply {
                    moveTo(segment.start.x.toFloat(), segment.start.y.toFloat())
                    if (segment.start != segment.end)
                        lineTo(segment.end.x.toFloat(), segment.end.y.toFloat())
                }
            previous?.continueStroke(path, 0, segment.durationMillis, segment.continues)
                ?: GestureDescription.StrokeDescription(
                    path,
                    0,
                    segment.durationMillis,
                    segment.continues,
                )
        },
        dispatch = { dispatchAndAwaitGesture(GestureDescription.Builder().addStroke(it).build()) },
    )
