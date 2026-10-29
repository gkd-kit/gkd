package li.gkd.app.matching.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ContinuousSwipeTest {
    private data class Stroke(val previous: Stroke?, val segment: SwipeSegment)

    @Test
    fun continuousSegmentsKeepTheirPredecessorAndExactFinalPointAndDuration() = runTest {
        val strokes = mutableListOf<Stroke>()
        assertTrue(
            continuousSwipe<Stroke>(
                10f,
                20f,
                110f,
                220f,
                250,
                create = { previous, segment -> Stroke(previous, segment).also(strokes::add) },
                dispatch = {
                    delay(it.segment.durationMillis)
                    GestureSegmentResult.Completed
                },
                nowMillis = { testScheduler.currentTime },
            ),
        )
        assertEquals(listOf(100L, 100L, 50L), strokes.map { it.segment.durationMillis })
        assertEquals(listOf(true, true, false), strokes.map { it.segment.continues })
        assertEquals(null, strokes.first().previous)
        assertTrue(
            strokes.zipWithNext().all { (a, b) ->
                b.previous === a && a.segment.end == b.segment.start
            },
        )
        assertEquals(SwipePoint(110, 220), strokes.last().segment.end)
        assertEquals(250L, testScheduler.currentTime)
    }

    @Test
    fun cancellationWaitsForSubmittedSegmentThenForStationaryReleaseAcknowledgement() = runTest {
        val segmentFinished = CompletableDeferred<Unit>()
        val releaseFinished = CompletableDeferred<Unit>()
        val strokes = mutableListOf<Stroke>()
        val job = launch {
            continuousSwipe<Stroke>(
                0f,
                0f,
                100f,
                100f,
                1000,
                create = { previous, segment -> Stroke(previous, segment).also(strokes::add) },
                dispatch = {
                    if (it.segment.continues) segmentFinished.await() else releaseFinished.await()
                    GestureSegmentResult.Completed
                },
                nowMillis = { testScheduler.currentTime },
            )
        }
        runCurrent()
        job.cancel()
        runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(1, strokes.size)
        segmentFinished.complete(Unit)
        runCurrent()
        assertFalse(job.isCompleted)
        val release = strokes.last()
        assertEquals(2, strokes.size)
        assertTrue(release.previous === strokes.first())
        assertEquals(
            SwipeSegment(SwipePoint(10, 10), SwipePoint(10, 10), 1, false),
            release.segment,
        )
        releaseFinished.complete(Unit)
        runCurrent()
        assertTrue(job.isCompleted && job.isCancelled)
    }

    @Test
    fun stationaryAndSubpixelIntervalsWaitWithoutSubmittingEmptyContinuations() = runTest {
        for (end in listOf(0f, 1f)) {
            val strokes = mutableListOf<Stroke>()
            val started = testScheduler.currentTime
            assertTrue(
                continuousSwipe<Stroke>(
                    0f,
                    0f,
                    end,
                    0f,
                    400,
                    create = { previous, segment -> Stroke(previous, segment).also(strokes::add) },
                    dispatch = { GestureSegmentResult.Completed },
                    nowMillis = { testScheduler.currentTime },
                ),
            )
            assertEquals(400L, testScheduler.currentTime - started)
            assertTrue(
                strokes
                    .drop(1)
                    .filter { it.segment.continues }
                    .all { it.segment.start != it.segment.end },
            )
            assertFalse(strokes.last().segment.continues)
        }
    }

    @Test
    fun cancellationDuringStationaryWaitReleasesTheExistingPointer() = runTest {
        val strokes = mutableListOf<Stroke>()
        val job = launch {
            continuousSwipe<Stroke>(
                20f,
                20f,
                20f,
                20f,
                500,
                create = { previous, segment -> Stroke(previous, segment).also(strokes::add) },
                dispatch = { GestureSegmentResult.Completed },
                nowMillis = { testScheduler.currentTime },
            )
        }
        runCurrent()
        advanceTimeBy(150)
        job.cancel()
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(2, strokes.size)
        assertTrue(strokes.last().previous === strokes.first())
        assertEquals(SwipePoint(20, 20), strokes.last().segment.start)
        assertEquals(strokes.last().segment.start, strokes.last().segment.end)
        assertFalse(strokes.last().segment.continues)
        assertEquals(1L, strokes.last().segment.durationMillis)
        assertEquals(150L, testScheduler.currentTime)
    }

    @Test
    fun cancellationDuringFinalStrokeDoesNotSubmitAnotherUp() = runTest {
        val finished = CompletableDeferred<Unit>()
        var submissions = 0
        val job = launch {
            continuousSwipe<Stroke>(
                0f,
                0f,
                100f,
                100f,
                50,
                create = { previous, segment -> Stroke(previous, segment) },
                dispatch = {
                    submissions++
                    finished.await()
                    GestureSegmentResult.Completed
                },
                nowMillis = { testScheduler.currentTime },
            )
        }
        runCurrent()
        job.cancel()
        finished.complete(Unit)
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(1, submissions)
    }

    @Test
    fun systemCancellationDoesNotCreateAnUnrelatedTouch() = runTest {
        var submissions = 0
        assertFalse(
            continuousSwipe<Stroke>(
                0f,
                0f,
                100f,
                100f,
                1000,
                create = { previous, segment -> Stroke(previous, segment) },
                dispatch = {
                    submissions++
                    GestureSegmentResult.Cancelled
                },
                nowMillis = { testScheduler.currentTime },
            ),
        )
        assertEquals(1, submissions)
    }

    @Test
    fun rejectedContinuationAttemptsReleaseAndReportsUnconfirmedCleanup() = runTest {
        var submissions = 0
        val result = async {
            runCatching {
                continuousSwipe<Stroke>(
                    0f,
                    0f,
                    100f,
                    100f,
                    1000,
                    create = { previous, segment -> Stroke(previous, segment) },
                    dispatch = {
                        if (++submissions == 1) GestureSegmentResult.Completed
                        else GestureSegmentResult.Rejected
                    },
                    nowMillis = { testScheduler.currentTime },
                )
            }
        }
            .await()
        assertEquals(3, submissions)
        assertTrue(result.exceptionOrNull()?.message?.contains("release is unconfirmed") == true)
    }
}
