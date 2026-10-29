package li.gkd.app.matching.action

import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import li.gkd.app.util.monotonicTimeMillis

data class SwipePoint(val x: Int, val y: Int)

data class SwipeSegment(
    val start: SwipePoint,
    val end: SwipePoint,
    val durationMillis: Long,
    val continues: Boolean,
)

enum class GestureSegmentResult {
    Completed,
    Cancelled,
    Rejected,
}

/**
 * A completed continuing stroke still owns a down pointer until a final stroke finishes. [dispatch]
 * awaits a platform completion/cancellation callback, or returns Rejected when no submission was
 * accepted. Only the currently submitted segment is shielded from cancellation; stationary waits
 * remain cancellable.
 */
suspend fun <S : Any> continuousSwipe(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    durationMillis: Long,
    create: (previous: S?, segment: SwipeSegment) -> S,
    dispatch: suspend (S) -> GestureSegmentResult,
    segmentMillis: Long = 100,
    nowMillis: () -> Long = ::monotonicTimeMillis,
): Boolean {
    require(durationMillis > 0 && segmentMillis > 0)
    fun point(time: Long): SwipePoint {
        val fraction = time.toDouble() / durationMillis
        return SwipePoint(
            (startX + (endX - startX) * fraction).roundToInt(),
            (startY + (endY - startY) * fraction).roundToInt(),
        )
    }
    var held: S? = null
    var position = point(0)
    var elapsed = 0L
    try {
        while (elapsed < durationMillis) {
            currentCoroutineContext().ensureActive()
            val duration = minOf(segmentMillis, durationMillis - elapsed)
            val end = point(elapsed + duration)
            val continues = elapsed + duration < durationMillis
            val started = nowMillis()
            // Android emits no MOVE for identical integer coordinates. A stationary
            // continuation with willContinue=true can otherwise have no events at all.
            if (held == null || end != position || !continues) {
                val stroke = create(held, SwipeSegment(position, end, duration, continues))
                val result =
                    withContext(NonCancellable) {
                        dispatch(stroke).also {
                            when (it) {
                                GestureSegmentResult.Completed -> {
                                    held = stroke.takeIf { continues }
                                    position = end
                                }
                                GestureSegmentResult.Cancelled -> held = null
                                GestureSegmentResult.Rejected -> Unit
                            }
                        }
                    }
                currentCoroutineContext().ensureActive()
                if (result != GestureSegmentResult.Completed) return false
            }
            // A stationary initial stroke can acknowledge DOWN before its nominal end.
            delay((duration - (nowMillis() - started)).coerceAtLeast(0))
            elapsed += duration
        }
        currentCoroutineContext().ensureActive()
        return true
    } finally {
        held?.let { previous ->
            withContext(NonCancellable) {
                val release = create(previous, SwipeSegment(position, position, 1, false))
                check(dispatch(release) != GestureSegmentResult.Rejected) {
                    "Continuous gesture release was not accepted; pointer release is unconfirmed"
                }
            }
        }
    }
}
