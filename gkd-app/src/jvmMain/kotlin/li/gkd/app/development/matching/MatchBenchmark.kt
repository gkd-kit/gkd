package li.gkd.app.development.matching

import com.sun.management.ThreadMXBean
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import li.gkd.app.DesktopStorage
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.diagnostics.MatchDebugResult
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchSimulatedNode
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.selector.NodeAdapter

@Serializable
data class MatchBenchmarkRequest(
    val request: MatchDebugRunRequest,
    val iterations: Int = 50,
    val warmup: Int = 20,
)

@Serializable
data class MatchBenchmarkSample(
    val level: String,
    val iterations: Int,
    val medianNanos: Long,
    val p95Nanos: Long,
    val allocatedBytesPerQuery: Long?,
    val nodeAccessesPerQuery: Long,
    val matched: Boolean,
    val reason: String,
)

@Serializable
data class MatchBenchmarkReport(
    val samples: List<MatchBenchmarkSample>,
    val scope: String =
        "JVM simulated single-rule production query; excludes transport, coordinator and setup; no physical input",
    val cache: String = "not_applicable: simulated adapter has no Android node cache",
    val allocation: String =
        "current worker thread only; null when JVM allocation accounting is unavailable",
)

private val benchmarkLock = Mutex()

fun Route.matchBenchmarkRoutes() {
    post("/debug/benchmark") {
        if (!DesktopStorage.isolated) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "Benchmark requires --test Desktop"),
            )
            return@post
        }
        try {
            call.respond(benchmarkMatching(call.receive()))
        } catch (error: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (error.message ?: "Invalid benchmark")),
            )
        } catch (error: IllegalStateException) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to (error.message ?: "Benchmark unavailable")),
            )
        }
    }
}

/** Bounded and isolated from the live coordinator and diagnostic session. */
suspend fun benchmarkMatching(input: MatchBenchmarkRequest): MatchBenchmarkReport {
    require(input.iterations in 1..200 && input.warmup in 0..100)
    require(!input.request.execute) { "Benchmark never executes actions" }
    require(input.request.subscription.length <= 1_000_000)
    val simulation = requireNotNull(input.request.simulation)
    require(
        simulation.rootDelayMillis == 0L &&
            simulation.actionMillis == 0L &&
            simulation.cleanupMillis == 0L,
    ) {
        "Benchmark requires zero simulated delays"
    }
    check(benchmarkLock.tryLock()) { "A benchmark is already running" }
    try {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            return withContext(dispatcher) {
                withTimeout(15_000) {
                    val rule =
                        prepareDebugRule(input.request, mapOf(simulation.app.id to simulation.app))
                    require(rule.matchDelay == 0L) { "Benchmark requires matchDelay=0" }
                    val bean =
                        (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf {
                            it.isThreadAllocatedMemorySupported && it.isThreadAllocatedMemoryEnabled
                        }
                    val thread = Thread.currentThread().id
                    val adapter =
                        CountingNodeAdapter(
                            SimulatedNodeAdapter(
                                simulation.root,
                                checkNotNull(currentCoroutineContext()[Job]),
                            ),
                        )
                    val levels = listOf("off", "flow", "detailed")
                    val diagnostics = levels.map { level ->
                        MatchDiagnostics().also {
                            if (level != "off")
                                it.start(
                                    MatchTimingPolicy(),
                                    detailed = level == "detailed",
                                    ruleFilter = rule.matchIdentity(),
                                )
                        }
                    }
                    val works = diagnostics.map { MatchWork(1, MatchTimingPolicy(), it) { true } }
                    val nodes = works.mapIndexed { i, work ->
                        SimulatedRuleNodes(simulation, adapter, work, diagnostics[i])
                    }
                    val times = levels.map { mutableListOf<Long>() }
                    val accesses = LongArray(3)
                    val allocations = LongArray(3)
                    var expected: MatchDebugResult? = null
                    // Rotate order to reduce systematic JIT/thermal bias between diagnostic levels.
                    repeat(input.warmup + input.iterations) { round ->
                        repeat(3) { offset ->
                            val level = (round + offset) % 3
                            val beforeAccess = adapter.accesses
                            val beforeBytes = bean?.getThreadAllocatedBytes(thread)
                            val start = System.nanoTime()
                            val result =
                                evaluateDebugRule(
                                    works[level],
                                    input.request,
                                    rule,
                                    simulation.app.id,
                                    simulation.activityId,
                                    nodes[level],
                                )
                            val elapsed = System.nanoTime() - start
                            val allocated = beforeBytes?.let {
                                checkNotNull(bean).getThreadAllocatedBytes(thread) - it
                            }
                            check(expected == null || expected == result) {
                                "Query result changed during benchmark; use stable rule eligibility"
                            }
                            expected = result
                            if (round >= input.warmup) {
                                times[level].add(elapsed)
                                accesses[level] += adapter.accesses - beforeAccess
                                allocations[level] += allocated ?: 0
                            }
                        }
                    }
                    check(accesses.distinct().size == 1) {
                        "Diagnostic level changed node access count"
                    }
                    val result = checkNotNull(expected)
                    MatchBenchmarkReport(
                        levels.mapIndexed { i, level ->
                            val ordered = times[i].sorted()
                            MatchBenchmarkSample(
                                level,
                                input.iterations,
                                ordered[ordered.size / 2],
                                ordered[(ordered.size * 95 + 99) / 100 - 1],
                                if (bean == null) null else allocations[i] / input.iterations,
                                accesses[i] / input.iterations,
                                result.matched,
                                result.reason,
                            )
                        },
                    )
                }
            }
        }
    } finally {
        benchmarkLock.unlock()
    }
}

/** Counts adapter operations only; original node objects pass through without wrappers. */
private class CountingNodeAdapter(private val source: NodeAdapter<MatchSimulatedNode>) :
    NodeAdapter<MatchSimulatedNode>() {
    var accesses = 0L
        private set

    override fun getAttr(target: Any, name: String): Any? {
        accesses++
        return source.getAttr(target, name)
    }

    override fun getName(node: MatchSimulatedNode): String? {
        accesses++
        return source.getName(node)
    }

    override fun getChildCount(node: MatchSimulatedNode): Int {
        accesses++
        return source.getChildCount(node)
    }

    override fun getChild(node: MatchSimulatedNode, index: Int): MatchSimulatedNode? {
        accesses++
        return source.getChild(node, index)
    }

    override fun getParent(node: MatchSimulatedNode): MatchSimulatedNode? {
        accesses++
        return source.getParent(node)
    }

    override fun getNodeKey(node: MatchSimulatedNode): Any {
        accesses++
        return source.getNodeKey(node)
    }
}
