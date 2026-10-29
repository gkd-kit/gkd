package li.gkd.app.matching.input

import android.app.Application
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityRecord
import kotlinx.coroutines.CancellationException
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityRecord

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28, 33],
    application = Application::class,
    manifest = Config.NONE,
    shadows = [A11yEventTest.SourceFailure::class],
)
class A11yEventTest {
    @Test
    fun onlyIndividualContentAndWindowStateEventsPassTheInputFilter() {
        val event =
            AccessibilityEvent.obtain().apply {
                packageName = "app.id"
                className = "View"
            }
        try {
            for (type in listOf(CONTENT_CHANGED, STATE_CHANGED)) {
                event.eventType = type
                assertTrue(event.isUseful())
            }
            for (type in
                listOf(
                    0,
                    AccessibilityEvent.TYPE_VIEW_CLICKED,
                    CONTENT_CHANGED or STATE_CHANGED,
                    CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED,
                )) {
                event.eventType = type
                assertFalse(event.isUseful())
            }
            event.eventType = CONTENT_CHANGED
            event.className = null
            assertFalse(event.isUseful())
            event.className = "View"
            event.packageName = null
            assertFalse(event.isUseful())
            assertFalse((null as AccessibilityEvent?).isUseful())
        } finally {
            event.recycle()
        }
    }

    @Test
    fun queuedEventKeepsItsKeyAfterTheOriginalEventIsCleared() {
        val event =
            AccessibilityEvent.obtain().apply {
                eventType = CONTENT_CHANGED
                packageName = "app.id"
                className = "View"
            }
        val key = MatchEventKey(MatchEventKind.Content, "app.id", "View")
        val queued = checkNotNull(event.toA11yEvent(key))
        try {
            event.recycle()
            assertEquals("app.id", queued.key.appId)
            assertEquals("View", queued.key.name)
            assertSame(key, queued.key)
        } finally {
            queued.close()
        }
    }

    /**
     * An unsealed source failure must reach the existing read-operation trace, then release
     * ownership.
     */
    @Test
    fun unavailableSourceRetainsItsCauseForDiagnosticsAndIsConsumedOnce() {
        val platformEvent = AccessibilityEvent.obtain().apply { className = "View" }
        val source = Shadow.extract<SourceFailure>(platformEvent)
        val previousReads = source.reads
        val failure = IllegalStateException("Cannot perform this action on a not sealed instance.")
        source.failure = failure
        val event = wrap(platformEvent)
        val trace = MatchDiagnostics()
        val session = trace.start(MatchTimingPolicy())
        assertSame(
            failure,
            assertThrows(IllegalStateException::class.java) {
                trace.observeOperation("Source") { event.takeSource() }
            },
        )
        assertEquals(
            listOf(failure.toString()),
            trace.read(session.id).events.mapNotNull { it.operation?.outcome },
        )
        assertTrue(trace.state.value!!.openOperations.isEmpty())
        assertNull(event.takeSource())
        event.close()
        assertEquals(previousReads + 1, source.reads)
    }

    /** Cancellation and programming failures must not be converted into a missing event source. */
    @Test
    fun sourceDoesNotSwallowCancellationOrUnexpectedFailures() {
        for (failure in
            listOf(
                CancellationException("stopped"),
                IllegalArgumentException("bad source"),
                LinkageError("bad binding"),
            )) {
            val platformEvent = AccessibilityEvent.obtain().apply { className = "View" }
            val source = Shadow.extract<SourceFailure>(platformEvent)
            val previousReads = source.reads
            source.failure = failure
            val event = wrap(platformEvent)
            assertSame(failure, assertThrows(Throwable::class.java) { event.takeSource() })
            assertNull(event.takeSource())
            event.close()
            assertEquals(previousReads + 1, source.reads)
        }
    }

    @Test
    fun clearedEventDoesNotAttemptToReadSource() {
        val platformEvent = AccessibilityEvent.obtain()
        val source = Shadow.extract<SourceFailure>(platformEvent)
        val previousReads = source.reads
        source.failure = IllegalStateException("not sealed")
        val event = wrap(platformEvent)
        assertNull(event.takeSource())
        event.close()
        assertEquals(previousReads, source.reads)
    }

    private fun wrap(event: AccessibilityEvent) =
        A11yEvent(
            MatchEventKey(MatchEventKind.Content, "app.id", "View"),
            0,
            0,
            event,
        )

    // The standard shadow returns a configured node without running framework sealed-state checks.
    @Implements(AccessibilityRecord::class)
    class SourceFailure : ShadowAccessibilityRecord() {
        var failure: Throwable? = null
        var reads = 0

        @Implementation
        override fun getSource(): AccessibilityNodeInfo? {
            reads++
            failure?.let { throw it }
            return super.getSource()
        }
    }
}
