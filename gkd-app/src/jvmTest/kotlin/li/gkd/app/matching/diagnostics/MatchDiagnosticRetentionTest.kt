package li.gkd.app.matching.diagnostics

import kotlin.test.*
import kotlinx.serialization.json.*
import li.gkd.app.matching.input.MatchTimingPolicy

class MatchDiagnosticRetentionTest {
    private fun query(id: Long, text: String = "value") =
        MatchQueryRecording(
            id,
            "rule",
            MatchSelectorPlan(listOf("[text]")),
            MatchRecordedValue(MatchRecordedValueType.Reference, reference = 1),
            listOf(
                MatchNodeCall(
                    MatchNodeOperation.Attr,
                    emptyList(),
                    0,
                    1,
                    "reader",
                    result = MatchRecordedValue(MatchRecordedValueType.String, text),
                ),
            ),
            null,
            null,
            null,
            startedAt = id,
            endedAt = id + 1,
        )

    /** Completed detail can roll out while the result history stays available and bounded. */
    @Test
    fun summariesSurviveDetailEvictionAndReportTheirOwnLimit() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        repeat(2_010) {
            diagnostics.registerQuery(session.id, MatchQueryEvidence(query(it.toLong())))
        }
        val capture = diagnostics.export(session.id)
        assertEquals(2_000, capture.queryRecordings.size)
        assertEquals(32, capture.queryRecordings.count { it.detailsRetained })
        assertEquals(10L, capture.droppedQuerySummaries)
        assertEquals(1_978L, capture.droppedQueryRecordings)
        assertTrue(capture.queryRecordings.dropLast(32).all { it.calls.isEmpty() && it.completed })
        assertEquals(10L, capture.queryRecordings.first().startedAt)
    }

    /** An observed fault pins nearby detail even after subsequent normal queries fill the ring. */
    @Test
    fun faultContextSurvivesNormalTrafficAndRemainsBounded() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        repeat(5) { diagnostics.registerQuery(session.id, MatchQueryEvidence(query(it.toLong()))) }
        diagnostics.record(0, MatchStage.Failed, "test_failure")
        repeat(40) { diagnostics.registerQuery(session.id, MatchQueryEvidence(query(it + 5L))) }
        val capture = diagnostics.export(session.id)
        assertEquals(
            (2L..6L).toList(),
            capture.queryRecordings.filter { it.retainedFor != null }.map { it.runId },
        )
        assertTrue(
            capture.queryRecordings
                .filter { it.retainedFor != null }
                .all { it.detailsRetained && it.calls.isNotEmpty() },
        )
        repeat(30) {
            diagnostics.record(0, MatchStage.Failed, "test_failure")
            diagnostics.registerQuery(session.id, MatchQueryEvidence(query(it + 45L)))
        }
        assertTrue(
            diagnostics.export(session.id).queryRecordings.count { it.retainedFor != null } <= 16,
        )
        assertTrue(
            diagnostics.export(session.id).queryRecordings.count { it.detailsRetained } <= 48,
        )
    }

    @Test
    fun slowQueryIsProtectedWithoutTreatingNormalMissesAsFailures() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        val evidence =
            MatchQueryEvidence(query(0).copy(durationNanos = 500_000_000, outcome = "not_matched"))
        diagnostics.registerQuery(session.id, evidence)
        diagnostics.finishQuery(session.id, evidence)
        repeat(40) { diagnostics.registerQuery(session.id, MatchQueryEvidence(query(it + 1L))) }
        val first = diagnostics.export(session.id).queryRecordings.first()
        assertEquals("slow_query", first.retainedFor)
        assertTrue(first.detailsRetained)
    }

    /** The wire keeps a shared plan and repeated long values once, with nullable fields absent. */
    @Test
    fun compactArchiveSeparatesHeadersAndDeduplicatesPlanAndLongValues() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        val text = "shared text ".repeat(100)
        val record = query(1, text).let { it.copy(calls = List(20) { _ -> it.calls.single() }) }
        val capture =
            diagnostics
                .export(session.id)
                .copy(queryRecordings = listOf(record, record.copy(runId = 2)))
        val archive = Json.parseToJsonElement(encodeDiagnosticCapture(capture)).jsonObject
        assertEquals(1, archive.getValue("queryPlans").jsonArray.size)
        assertFalse("queryRecordings" in archive)
        val summary = archive.getValue("querySummaries").jsonArray.first().jsonObject
        assertFalse("calls" in summary)
        assertFalse("plan" in summary)
        assertFalse("failure" in summary)
        val detail = archive.getValue("queryDetails").jsonArray.first().jsonObject
        assertFalse("rule" in detail)
        assertEquals(listOf(JsonPrimitive(text)), detail.getValue("texts").jsonArray.toList())
        assertTrue(
            detail.getValue("calls").jsonArray.all {
                it.jsonObject.getValue("result").jsonObject["valueRef"] == JsonPrimitive(0)
            },
        )
        assertTrue(detail.toString().toByteArray().size < text.toByteArray().size * 10)
    }
}
