package li.gkd.app.matching.query

import kotlinx.coroutines.CancellationException
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.input.MatchTimingPolicy
import org.junit.Assert.*
import org.junit.Test

class A11yWindowReadTest {
    @Test
    fun unavailableWindowsKeepTheFailureDistinctFromAnEmptyResult() {
        val trace = MatchDiagnostics()
        val session = trace.start(MatchTimingPolicy())
        assertNull(readA11yWindow(trace, "Root") { null })
        assertNull(readA11yWindow(trace, "Root") { throw SecurityException("cross-user denied") })
        assertNull(
            readA11yWindow(trace, "Windows") {
                throw IllegalStateException("UiAutomation disconnected")
            },
        )
        assertEquals(
            listOf(
                "null",
                "java.lang.SecurityException: cross-user denied",
                "java.lang.IllegalStateException: UiAutomation disconnected",
            ),
            trace.export(session.id).events.mapNotNull { it.operation?.outcome },
        )
        assertTrue(trace.state.value!!.openOperations.isEmpty())
    }

    @Test
    fun disabledDiagnosticsDoNotReadClocksAndCancellationAndFatalErrorsEscape() {
        val trace = MatchDiagnostics(nanoTime = { error("clock") }, wallTime = { error("clock") })
        assertNull(readA11yWindow(trace, "Root") { throw SecurityException("denied") })
        val cancelled = CancellationException("stopped")
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) {
                readA11yWindow(trace, "Root") { throw cancelled }
            },
        )
        val fatal = LinkageError("invalid binding")
        assertSame(
            fatal,
            assertThrows(LinkageError::class.java) {
                readA11yWindow(trace, "Root") { throw fatal }
            },
        )
    }
}
