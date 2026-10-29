package li.gkd.app.subscription

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import li.gkd.app.model.ScreenSize
import li.gkd.app.rule.AppRule
import li.gkd.app.rule.ResolvedAppGroup
import li.gkd.app.rule.RuleRuntime
import li.gkd.db.SubsItem
import org.junit.Assert.*
import org.junit.Test

class RuleCompilationTest {
    private fun subscription(rules: String) =
        RawSubscriptionParser.parse(
            """{id:1,name:'Test',version:1,apps:[{id:'app',groups:[{key:1,name:'Group',rules:$rules}]}]}""",
        )

    private fun group(rules: String) = subscription(rules).apps.single().groups.single()

    @Test
    fun executionUsesValidatedSelectorsWithoutDependingOnValidationReadOrder() {
        val subscription =
            subscription(
                """[{matches:['[depth=0]','[depth=0]'],anyMatches:'[text="ok"]',excludeMatches:'[depth=0]'}]""",
            )
        val app = subscription.apps.single()
        val group = app.groups.single()
        // Construct the executable rule before anyone reads valid/validationError.
        val resolved =
            AppRule(
                group.rules.single(),
                ResolvedAppGroup(
                    group,
                    subscription,
                    SubsItem(1, enable = true, order = 0),
                    null,
                    app,
                    true,
                ),
                null,
                RuleRuntime(),
            )
        assertTrue(group.valid)
        assertSame(resolved.matches[0], resolved.matches[1])
        assertSame(resolved.matches[0], resolved.excludeMatches.single())
        assertSame(resolved.matches[0], group.compilation.selector("[depth=0]"))
        assertSame(resolved.anyMatches.single(), group.compilation.selector("[text=\"ok\"]"))
    }

    @Test
    fun invalidGroupsReportFirstErrorAndDoNotExposePartialCompilation() {
        for (source in listOf("[", "[nonexistentProperty=1]")) {
            val group = group("""[{matches:['[depth=0]','$source']}]""")
            assertFalse(group.valid)
            val failure = group.validationError as RuleValidationError.SelectorError
            assertEquals(source, failure.source)
            assertTrue(failure.detail.isNotEmpty())
            assertThrows(IllegalStateException::class.java) {
                group.compilation.selector("[depth=0]")
            }
        }
        val invalidPosition = group("""[{matches:'[depth=0]',position:{x:'unknown',y:'0'}}]""")
        assertTrue(invalidPosition.validationError is RuleValidationError.InvalidPosition)
    }

    @Test
    fun copiedDefinitionGetsItsOwnCompilationAndSerializationStaysRaw() {
        val original = group("""[{matches:'[depth=0]',position:{x:'width/2',y:'height/2'}}]""")
        val before = SubscriptionJson.toJson5String(original)
        assertTrue(original.valid)
        original.rules.single().position!!.calc(RuleBounds(0, 0, 100, 200), ScreenSize(100, 200))
        assertEquals(before, SubscriptionJson.toJson5String(original))
        val edited =
            original.copy(rules = listOf(original.rules.single().copy(matches = listOf("["))))
        assertFalse(edited.valid)
        assertTrue(original.valid)
        assertNotSame(original.compilation, edited.compilation)
        assertNotNull(original.compilation.selector("[depth=0]"))
    }

    private fun position(
        left: String? = null,
        top: String? = null,
        right: String? = null,
        bottom: String? = null,
        x: String? = null,
        y: String? = null,
    ) = RawSubscription.Position(left, top, right, bottom, x, y)

    @Test
    fun positionPreservesAnchorPriorityButRejectsEveryInvalidSuppliedField() {
        val bounds = RuleBounds(10, 20, 110, 220)
        assertEquals(
            35f to 70f,
            position(
                    left = "width/4",
                    top = "height/4",
                    right = "0",
                    bottom = "0",
                    x = "0",
                    y = "0",
                )
                .calc(bounds, ScreenSize(300, 500)),
        )
        val invalid =
            position(left = "unknown", top = "(", right = "10", bottom = "20", x = "0", y = "0")
        assertFalse(invalid.isValid)
        assertEquals(
            "position.left",
            assertThrows(PositionCalculationException::class.java) {
                    invalid.calc(bounds, ScreenSize(300, 500))
                }
                .error
                .field,
        )
        assertFalse(position(left = "0", top = "0", x = "unknown").isValid)
        assertEquals(
            150f to 250f,
            position(x = "screenWidth/2", y = "screenHeight/2").calc(bounds, ScreenSize(300, 500)),
        )
        assertFalse(position(x = "unknown", y = "0").isValid)
        assertEquals("MISSING_VERTICAL_COORDINATE", position(x = "0").validationError?.code)
    }

    @Test
    fun failedEvaluationDoesNotContaminateNextInvocation() {
        val position = position(x = "100/width", y = "screenHeight")
        assertTrue(position.isValid)
        val error =
            assertThrows(PositionCalculationException::class.java) {
                position.calc(RuleBounds(0, 0, 0, 10), ScreenSize(100, 200))
            }
        assertEquals("DIVISION_BY_ZERO", error.error.code)
        assertEquals(5f to 400f, position.calc(RuleBounds(0, 0, 20, 30), ScreenSize(300, 400)))
        val randomPosition = position(x = "random", y = "random")
        repeat(20) {
            val (x, y) = randomPosition.calc(RuleBounds(0, 0, 1, 1), ScreenSize(1, 1))
            assertTrue(x in 0f..1f && y in 0f..1f)
        }
    }

    @Test
    fun sharedPositionEvaluatesDifferentBoundsConcurrentlyWithoutVariableLeakage() {
        val shared = position(x = "left+width+screenWidth", y = "top+height+screenHeight")
        val executor = Executors.newFixedThreadPool(4)
        val barrier = CyclicBarrier(4)
        try {
            val tasks =
                (1..4).map { worker ->
                    executor.submit(
                        Callable {
                            repeat(500) { iteration ->
                                val base = worker * 10000 + iteration
                                val bounds = RuleBounds(base, base + 1, base + 100, base + 201)
                                barrier.await(10, TimeUnit.SECONDS)
                                assertEquals(
                                    (base + 100 + worker).toFloat() to
                                        (base + 201 + worker * 2).toFloat(),
                                    shared.calc(bounds, ScreenSize(worker, worker * 2)),
                                )
                            }
                        },
                    )
                }
            tasks.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
