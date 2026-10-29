package li.gkd.app.matching.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.rule.RuleStateOperation
import li.gkd.app.rule.RuleTransition

class MatchWireTypesTest {
    @Test
    fun existingDiagnosticJsonRetainsWireNames() {
        // Existing exports use lowercase phase/type and camelCase node operations.
        val requestJson =
            """{"request":{"requestId":"test","subscription":"{}","rule":"rule"},"phase":"cancelling"}"""
        val request = Json.decodeFromString<MatchDebugRunState>(requestJson)
        assertEquals(MatchDebugRunPhase.Cancelling, request.phase)
        assertEquals(
            "cancelling",
            Json.parseToJsonElement(Json.encodeToString(request))
                .jsonObject
                .getValue("phase")
                .jsonPrimitive
                .content,
        )

        val callJson =
            """{"operation":"queryRoot","arguments":[],"startedNanos":0,"durationNanos":1,"thread":"test","result":{"type":"node_identity","reference":42}}"""
        val call = Json.decodeFromString<MatchNodeCall>(callJson)
        assertEquals(MatchNodeOperation.QueryRoot, call.operation)
        assertEquals(MatchRecordedValueType.NodeIdentity, call.result!!.type)
        val encoded = Json.parseToJsonElement(Json.encodeToString(call)).jsonObject
        assertEquals("queryRoot", encoded.getValue("operation").jsonPrimitive.content)
        assertEquals(
            "node_identity",
            encoded.getValue("result").jsonObject.getValue("type").jsonPrimitive.content,
        )

        assertEquals(
            RuleStateOperation.Status,
            Json.decodeFromString<RuleStateOperation>("\"status\""),
        )
        assertEquals("\"trigger\"", Json.encodeToString(RuleTransition.Trigger.recordOperation))
    }
}
