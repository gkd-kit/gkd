package li.gkd.app.subscription

import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.serialization.json.*
import li.gkd.app.model.ScreenSize
import li.gkd.app.testing.TestFiles
import li.songe.expression.ExpressionEvaluationException
import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import org.junit.Assert.*
import org.junit.Test

/** Protect subscription calculations and rejection boundaries when changing expression engines. */
class RuleExpressionCompatibilityTest {
    private val variables =
        setOf(
            "left",
            "top",
            "right",
            "bottom",
            "width",
            "height",
            "random",
            "screenWidth",
            "screenHeight",
        )
    private val corpus =
        Json.parseToJsonElement(
                javaClass.getResource("/expressions/subscription-corpus.json")!!.readText(),
            )
            .jsonObject
            .getValue("expressions")
            .jsonArray
            .map { it.jsonPrimitive.content }
    private val boundaries =
        listOf(
            "2+3*4",
            "20/5*2",
            "20-5-2",
            "20%6*2",
            "2+3*-4/8",
            "-2*2",
            "(-2)*2",
            "2/-2",
            "2--3",
            "+2",
            "-(width+height)",
            "1e-3",
            "1E+2",
            "0.5*width",
            "2*(width+1)",
            "7%3",
            "-7%3",
            "1/0",
            "1/-0",
            "1%0",
            "1e309",
            "1e309-1e309",
            "random-random",
            "unknown+1",
            "",
            "(",
            "1+",
            "2 3",
            "2$",
            "1.2.3",
            "1e-",
        )
    private val unsupported =
        listOf(
            "-2^2",
            "(-2)^2",
            "2^-2",
            "2^3^2",
            "0^0",
            "0^-1",
            "(-1)^0.5",
            "10^400",
            "2^",
            "^2",
            "2^^3",
            "2^--3",
            "pow(2,3)",
            "sin(1)",
            "cos(1)",
            "tan(1)",
            "cot(1)",
            "asin(0.5)",
            "acos(0.5)",
            "atan(1)",
            "sinh(1)",
            "cosh(1)",
            "tanh(1)",
            "abs(-2)",
            "sqrt(4)",
            "cbrt(8)",
            "ceil(1.1)",
            "floor(1.1)",
            "log(1)",
            "log2(8)",
            "log10(100)",
            "log1p(1)",
            "exp(1)",
            "expm1(1)",
            "signum(-1)",
            "pi",
            "π",
            "φ",
            "e",
            "NaN",
            "Infinity",
            "ln(1)",
            "round(1.5)",
            "2(width+1)",
            "width height",
            "widthheight",
            "width2",
            "2e",
            "2pi",
            "[2+3]",
            "{2+3}",
            ".5",
            "2.",
            "1.e2",
            "--2",
            "2**3",
            "width=1",
            "min 1,2",
            "min()",
            "min(1)",
            "min(1,2,3)",
            "min(1,,2)",
            "min(1,2,)",
            "max(,2)",
            "clamp(1,2)",
            "clamp(1,2,3,4)",
            "Min(1,2)",
            "width(2)",
            "min(1 max(2,3))",
            "min(1,max(2,3),)",
            "min((1,2),3)",
            "clamp(1,max(2),3)",
            "PI",
            "E",
            "screen.width",
            "math.min(1,2)",
            "width\u00a0*0.5",
        )
    private val generated = buildList {
        val random = Random(42)
        val atoms = listOf("width", "height", "left", "screenWidth", "0", "0.5", "-2", "3")
        fun expression(depth: Int): String =
            if (depth == 0) atoms.random(random)
            else
                when (random.nextInt(4)) {
                    0 -> "(${expression(depth - 1)})"
                    1 -> "-(${expression(depth - 1)})"
                    else ->
                        "(${expression(depth - 1)})${listOf("+", "-", "*", "/", "%").random(random)}(${expression(depth - 1)})"
                }
        repeat(300) { add(expression(3)) }
    }

    private fun bindings(n: Int): Map<String, Double> =
        mapOf(
            "left" to n.toDouble(),
            "top" to (n + 1.0),
            "right" to (n + 321.0),
            "bottom" to (n + 647.0),
            "width" to 321.0,
            "height" to 646.0,
            "random" to (n % 10 / 10.0),
            "screenWidth" to (1080.0 + n),
            "screenHeight" to (2400.0 + n),
        )

    private fun old(source: String): Expression? =
        try {
            ExpressionBuilder(source).variables(variables).build().takeIf {
                it.validate(false).isValid
            }
        } catch (_: Exception) {
            null
        }

    private data class Outcome(
        val phase: String,
        val value: Double? = null,
        val error: String? = null,
    )

    private fun outcome(
        compile: () -> ((Map<String, Double>) -> Double)?,
        values: Map<String, Double>,
    ): Outcome {
        val expression =
            try {
                compile()
            } catch (_: Exception) {
                null
            } ?: return Outcome("compile_error")
        return try {
            Outcome("value", expression(values))
        } catch (e: Exception) {
            // The new engine exposes a typed error; compare its meaning with exp4j's exception.
            val error =
                when {
                    e is ExpressionEvaluationException -> e.code.name
                    e is ArithmeticException && e.message == "Division by zero!" ->
                        "DIVISION_BY_ZERO"
                    else -> "${e.javaClass.simpleName}: ${e.message}"
                }
            Outcome("evaluation_error", error = error)
        }
    }

    private fun same(a: Outcome, b: Outcome): Boolean =
        a.phase == b.phase &&
            when (a.phase) {
                "value" ->
                    a.value == b.value ||
                        (a.value!!.isNaN() && b.value!!.isNaN()) ||
                        (a.value.isFinite() &&
                            b.value!!.isFinite() &&
                            abs(a.value - b.value) <= 1e-12 * maxOf(1.0, abs(a.value)))
                else -> a.error == b.error
            }

    @Test
    fun languageBoundaryRejectsRemovedAndImplicitSyntaxBeforeEvaluation() {
        for (source in unsupported) assertNull(
            "Must reject: $source",
            compileRuleExpression(source),
        )
    }

    @Volatile private var sink = 0.0

    @Test
    fun staticBindingValidationFindsErrorsBeyondArithmeticAndRepeatedCalls() {
        for (source in
            listOf(
                "min(width,100)",
                "max(height,100)",
                "clamp(width,0,screenWidth)",
                "1/0 + clamp(1,2,3)",
                "min(1,2)+min(3,4)",
                "max(1,min(2,3))",
                "unknown(width)",
                "1/0 + unknown",
                "min(width,screen.width)",
            )) assertNull(source, compileRuleExpression(source))
        // Valid variable bindings do not execute division during compilation.
        assertNotNull(compileRuleExpression("1/0 + width"))
        val group =
            RawSubscriptionParser.parse(
                    """{id:1,name:'T',version:1,globalGroups:[{key:1,name:'G',rules:[{position:{x:'1/0 + clamp(1,2,3)',y:0}}]}]}""",
                )
                .globalGroups
                .single()
        assertTrue(group.validationError is RuleValidationError.InvalidPosition)
    }

    @Test
    fun adaptedEnginePreservesExistingValuesAndErrorStages() {
        val differences = mutableListOf<String>()
        val sources = (corpus + boundaries + generated).distinct()
        for (source in sources) {
            for (n in listOf(-200, 0, 3, 500, 10000)) {
                val values = bindings(n)
                val baseline =
                    outcome(
                        {
                            old(source)?.let { t ->
                                { v -> Expression(t).setVariables(v).evaluate() }
                            }
                        },
                        values,
                    )
                val candidate =
                    outcome(
                        {
                            compileRuleExpression(source)?.let { expression ->
                                { v ->
                                    expression.evaluate(
                                        RuleBounds(
                                            v.getValue("left").toInt(),
                                            v.getValue("top").toInt(),
                                            v.getValue("right").toInt(),
                                            v.getValue("bottom").toInt(),
                                        ),
                                        ScreenSize(
                                            v.getValue("screenWidth").toInt(),
                                            v.getValue("screenHeight").toInt(),
                                        ),
                                    )
                                }
                            }
                        },
                        values,
                    )
                if (!same(baseline, candidate))
                    differences.add("$source, n=$n: $baseline -> $candidate")
            }
        }
        val file = TestFiles.root.resolve("expression-comparison.json")
        file.writeText(
            buildJsonObject {
                put("subscriptionExpressions", corpus.size)
                put("evaluations", sources.size * 5)
                put("differences", JsonArray(differences.map(::JsonPrimitive)))
            }
                .toString(),
        )
        println("EXPRESSION_COMPARISON=$file")
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    @Test
    fun improvementsRejectMalformedCallsAndUnsafeCoordinatesAndValidateSwipes() {
        assertNull(compileRuleExpression("pow(2,,3)"))
        val bounds = RuleBounds(0, 0, 100, 200)
        for (source in listOf("1e309-1e309", "1e309", "1e39", "1e308*1e308")) {
            val position = RawSubscription.Position(null, null, null, null, source, "0")
            val failure =
                assertThrows(PositionCalculationException::class.java) {
                    position.calc(bounds, ScreenSize(100, 200))
                }
            assertEquals(source, "NON_FINITE_COORDINATE", failure.error.code)
        }
        for (position in
            listOf(
                "start:{x:'unknown',y:0}",
                "start:{x:0,y:0},end:{x:0,y:'unknown'}",
                "start:{x:'clamp(width,0,100)',y:0}",
                "start:{x:0,y:0},end:{x:0,y:'min(1,2)'}",
            )) {
            val group =
                RawSubscriptionParser.parse(
                        """{id:1,name:'T',version:1,globalGroups:[{key:1,name:'G',rules:[{swipeArg:{$position,duration:100}}]}]}""",
                    )
                    .globalGroups
                    .single()
            assertTrue(group.validationError is RuleValidationError.InvalidPosition)
        }
    }

    @Test
    fun evaluationKeepsErrorLocationsAndRandomBinding() {
        val bounds = RuleBounds(0, 0, 100, 200)
        for (source in listOf("1 / 0", "1 % 0", "1 / -0")) {
            val position = RawSubscription.Position(null, null, null, null, source, "0")
            assertTrue(position.isValid)
            val error =
                assertThrows(PositionCalculationException::class.java) {
                        position.calc(bounds, ScreenSize(100, 200))
                    }
                    .error
            assertEquals("DIVISION_BY_ZERO", error.code)
            assertEquals(2, error.startIndex)
            assertEquals(3, error.endIndex)
        }
        assertEquals(
            0.0,
            compileRuleExpression("random-random")!!.evaluate(bounds, ScreenSize(100, 200)),
            0.0,
        )
    }

    @Test
    fun benchmarkCompilationAndIsolatedEvaluation() {
        // Match the replaced production path: copy the template and bind variables directly.
        fun legacy(source: String): (RuleBounds, ScreenSize) -> Double {
            val template = requireNotNull(old(source))
            return { bounds, screenSize ->
                Expression(template)
                    .setVariable("left", bounds.left.toDouble())
                    .setVariable("top", bounds.top.toDouble())
                    .setVariable("right", bounds.right.toDouble())
                    .setVariable("bottom", bounds.bottom.toDouble())
                    .setVariable("width", bounds.width().toDouble())
                    .setVariable("height", bounds.height().toDouble())
                    .setVariable("screenWidth", screenSize.width.toDouble())
                    .setVariable("screenHeight", screenSize.height.toDouble())
                    .setVariable("random", Random.nextDouble())
                    .evaluate()
            }
        }
        val oldCompiled = corpus.map(::legacy)
        val adapted = corpus.map { requireNotNull(compileRuleExpression(it)) }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().threadId()
        fun measure(operations: Int, block: () -> Double): Pair<Double, Double> {
            val bytes = bean.getThreadAllocatedBytes(thread)
            val start = System.nanoTime()
            sink = block()
            return (System.nanoTime() - start).toDouble() / operations to
                (bean.getThreadAllocatedBytes(thread) - bytes).toDouble() / operations
        }
        val compileIterations = 25
        val evaluationIterations = 1000
        fun run(engine: String, compile: Boolean): Pair<Double, Double> =
            measure(corpus.size * if (compile) compileIterations else evaluationIterations) {
                var total = 0.0
                repeat(if (compile) compileIterations else evaluationIterations) { round ->
                    corpus.indices.forEach { index ->
                        val bounds = RuleBounds(round, round + 1, round + 321, round + 647)
                        total +=
                            if (engine == "exp4j") {
                                val expression =
                                    if (compile) legacy(corpus[index]) else oldCompiled[index]
                                expression(bounds, ScreenSize(1080 + round, 2400 + round))
                            } else {
                                val expression =
                                    if (compile) compileRuleExpression(corpus[index])!!
                                    else adapted[index]
                                expression.evaluate(bounds, ScreenSize(1080 + round, 2400 + round))
                            }
                    }
                }
                total
            }
        val report = buildJsonArray {
            for (compile in listOf(false, true)) {
                val samples =
                    mutableMapOf(
                        "exp4j" to mutableListOf<Pair<Double, Double>>(),
                        "expression" to mutableListOf(),
                    )
                var checksum: Double? = null
                repeat(10) { round ->
                    val order =
                        if (round % 2 == 0) samples.keys.toList() else samples.keys.reversed()
                    order.forEach { engine ->
                        val result = run(engine, compile)
                        if (checksum == null) checksum = sink
                        else assertEquals(checksum, sink, abs(checksum) * 1e-12)
                        if (round >= 5) samples.getValue(engine).add(result)
                    }
                }
                samples.forEach { (engine, values) ->
                    add(
                        buildJsonObject {
                            put("engine", engine)
                            put("operation", if (compile) "compile_and_evaluate" else "evaluate")
                            put(
                                "operationsPerSample",
                                corpus.size *
                                    if (compile) compileIterations else evaluationIterations,
                            )
                            put("medianNanos", values.map { it.first }.sorted()[2])
                            put("medianBytes", values.map { it.second }.sorted()[2])
                        },
                    )
                }
            }
        }
        val file = TestFiles.root.resolve("expression-benchmark.json")
        file.writeText(
            buildJsonObject {
                put("java", System.getProperty("java.runtime.version"))
                put("os", "${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                put("subscriptionExpressions", corpus.size)
                put("warmupRounds", 5)
                put("measuredRounds", 5)
                put("samples", report)
            }
                .toString(),
        )
        println("EXPRESSION_BENCHMARK=$file")
        assertTrue(sink.isFinite())
    }
}
