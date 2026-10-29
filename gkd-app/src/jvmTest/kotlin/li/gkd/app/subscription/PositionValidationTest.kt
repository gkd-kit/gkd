package li.gkd.app.subscription

import li.gkd.app.error.ErrorMessage
import li.gkd.app.model.ScreenSize
import li.gkd.app.rule.RuleGroupPolicy
import li.gkd.app.rule.RuleRestriction
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.*
import org.junit.Test

class PositionValidationTest {
    /** Every editing entry, the card message and enable policy share the same invalid field. */
    @Test
    fun invalidUnusedFieldsBlockExecutionAndAllEditingEntriesWithTheSameDetail() {
        val groupText = """{key:1,name:'G',rules:[{position:{left:'0',top:'0',x:'width + )'}}]}"""
        val appText = """{id:'app',groups:[$groupText]}"""
        val subscription =
            RawSubscriptionParser.parse("""{id:1,name:'T',version:1,apps:[$appText]}""")
        val group = subscription.apps.single().groups.single()
        val failure = group.validationError as RuleValidationError.InvalidPosition
        assertEquals("rules[0].position.x", failure.error.field)
        assertEquals("width + )", failure.error.source)
        assertEquals("UNEXPECTED_TOKEN", failure.error.code)
        assertEquals(8, failure.error.startIndex)
        assertEquals(9, failure.error.endIndex)
        val control =
            RuleGroupPolicy.controlState(
                subscription,
                group,
                "app",
                SubscriptionConfigSnapshot(),
                null,
                "",
                emptySet(),
            )
        assertTrue(control.canEnable)
        assertTrue(control.configuredEnabled)
        assertFalse(RuleGroupPolicy.getGroupEnabled(group, null))
        assertFalse(control.available)
        assertEquals(
            failure.message,
            control.restrictions.filterIsInstance<RuleRestriction.Invalid>().single().message,
        )
        for (parse in
            listOf<() -> Any>(
                { parseInput(groupText).parseAppGroup("app") },
                { parseInput(groupText).parseAppGroups("app") },
                { parseInput(groupText).parseGlobalGroup() },
                { parseInput(appText).parseApp() },
            )) {
            val rejected = assertRejected { parse() }
            assertEquals(SubscriptionFailureReason.RuleInvalidPosition, rejected.reason)
            assertEquals(failure.error.field, rejected.errorPath)
            assertEquals(failure.message, rejected.errorMessage)
        }
    }

    @Test
    fun swipeEndAndMissingAxesHavePreciseFieldPathsWhileRuntimeErrorsCanBeSaved() {
        val bad =
            parseInput(
                """{key:1,name:'G',rules:[{swipeArg:{start:{x:0,y:0},end:{x:0,y:'min(1,2)'},duration:100}}]}""",
            )
        val error = assertRejected { bad.parseGlobalGroup() }
        assertEquals("rules[0].swipeArg.end.y", error.errorPath)
        assertTrue(
            (error.errorMessage as ErrorMessage.Resource)
                .arguments
                .toString()
                .contains("UNKNOWN_FUNCTION"),
        )
        val missing = RawSubscription.Position(null, null, null, null, "0", null)
        assertEquals("MISSING_VERTICAL_COORDINATE", missing.validationError?.code)
        val group =
            parseInput("""{key:1,name:'G',rules:[{position:{x:'100/width',y:0}}]}""")
                .parseGlobalGroup()
                .expectValue()
        assertTrue(group.valid)
        val position = group.rules.single().position!!
        val failure =
            assertThrows(PositionCalculationException::class.java) {
                    position.calc(
                        RuleBounds(0, 0, 0, 10),
                        ScreenSize(100, 200),
                        captureInputs = true,
                    )
                }
                .error
        assertEquals("DIVISION_BY_ZERO", failure.code)
        assertEquals(mapOf("width" to 0.0), failure.inputs)
        assertEquals(5f to 0f, position.calc(RuleBounds(0, 0, 20, 10), ScreenSize(100, 200)))
        val quiet =
            assertThrows(PositionCalculationException::class.java) {
                    position.calc(RuleBounds(0, 0, 0, 10), ScreenSize(100, 200))
                }
                .error
        assertNull(quiet.inputs)
    }

    @Test
    fun diagnosticBindingsAreTheActualRandomSampleAndRemainJsonSafeForNonFiniteResults() {
        val source = "random / (width-width)"
        val position = RawSubscription.Position(null, null, null, null, source, "0")
        val error =
            assertThrows(PositionCalculationException::class.java) {
                    position.calc(
                        RuleBounds(0, 0, 30, 40),
                        ScreenSize(100, 200),
                        captureInputs = true,
                    )
                }
                .error
        assertEquals(setOf("random", "width"), error.inputs!!.keys)
        assertTrue(error.inputs.getValue("random") in 0.0..<1.0)
        assertEquals(30.0, error.inputs.getValue("width"), 0.0)
        val overflow = RawSubscription.Position(null, null, null, null, "width*1e309", "0")
        val nonFinite =
            assertThrows(PositionCalculationException::class.java) {
                    overflow.calc(
                        RuleBounds(0, 0, 30, 40),
                        ScreenSize(100, 200),
                        captureInputs = true,
                    )
                }
                .error
        assertEquals("NON_FINITE_COORDINATE", nonFinite.code)
        assertEquals(mapOf("width" to 30.0), nonFinite.inputs)
        val encoded =
            kotlinx.serialization.json.Json.encodeToString(PositionError.serializer(), nonFinite)
        assertEquals(
            nonFinite,
            kotlinx.serialization.json.Json.decodeFromString(PositionError.serializer(), encoded),
        )
    }
}
