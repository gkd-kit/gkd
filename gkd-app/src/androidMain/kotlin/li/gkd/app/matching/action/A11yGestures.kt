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

private suspend fun AccessibilityService.dispatchAndAwaitGesture(gesture: GestureDescription): GestureSegmentResult {
    val finished = CompletableDeferred<GestureSegmentResult>()
    val submitted = observeActionOperation("AccessibilityService.dispatchGesture") {
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                finished.complete(GestureSegmentResult.Completed)
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                finished.complete(GestureSegmentResult.Cancelled)
            }
        }, null)
    }
    if (!submitted) return GestureSegmentResult.Rejected
    return observeActionOperation("GestureCallbackWait") { finished.await() }
}
