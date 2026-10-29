package li.gkd.app.development.matching

import android.os.Build
import android.os.Debug
import android.os.Trace
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchDebugResult
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchNodeMetrics
import li.gkd.app.matching.diagnostics.NodeMetricSnapshot
import li.gkd.app.matching.diagnostics.NodeReadOperation
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.isInteractive
import li.gkd.app.matching.query.A11yQueryContext
import li.gkd.app.matching.query.MatchNodeReader
import li.gkd.app.matching.query.RuleQueryNodes
import li.gkd.app.matching.query.setGeneratedTime
import li.gkd.app.model.ActionResult
import li.gkd.app.rule.*
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.ScreenUtils

@Serializable
enum class AndroidBenchmarkCache {
    Cold,
    Warm,
    RefreshRoot,
}

@Serializable
data class AndroidBenchmarkRequest(
    val request: MatchDebugRunRequest,
    val iterations: Int = 10,
    val warmup: Int = 2,
    val cacheModes: List<AndroidBenchmarkCache> = AndroidBenchmarkCache.entries,
)

@Serializable
data class AndroidBenchmarkMeasurement(
    val nanos: Long,
    val processBinderSent: Long?,
    val processBinderReceived: Long?,
    val processAllocatedBytes: Long?,
    val nodes: NodeMetricSnapshot,
)

@Serializable
data class AndroidBenchmarkSample(
    val cache: AndroidBenchmarkCache,
    val level: String,
    val medianNanos: Long,
    val p95Nanos: Long,
    val measurements: List<AndroidBenchmarkMeasurement>,
)

@Serializable
data class AndroidBenchmarkReport(
    val sdk: Int,
    val model: String,
    val serviceMode: Int,
    val appId: String,
    val windowId: Int,
    val matched: Boolean,
    val reason: String,
    val rule: String,
    val warmupIterations: Int,
    val idleMillis: Long,
    val idleProcessBinderSent: Long?,
    val idleProcessBinderReceived: Long?,
    val samples: List<AndroidBenchmarkSample>,
    val scope: String =
        "live read-only production query; GKD caches only; framework cache/prefetch remains enabled",
    val binderScope: String =
        "Debug counters cover the whole app process, including unrelated threads; node API calls are not Binder transaction counts",
    val allocationScope: String =
        "ART allocated-bytes is process-wide gross allocation, not retained memory or query-only allocation; null means unavailable",
    val stability: String =
        "service/page, root identity/child count and matched node/result checked; live tree is not an atomic snapshot",
)

private val androidBenchmarkLock = Mutex()

private inline fun <T> traceRead(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}

private data class ProcessCounters(val sent: Long?, val received: Long?, val allocated: Long?) {
    companion object {
        fun read() =
            ProcessCounters(
                Debug.getBinderSentTransactions().takeIf { it >= 0 }?.toLong(),
                Debug.getBinderReceivedTransactions().takeIf { it >= 0 }?.toLong(),
                Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull(),
            )
    }
}

private fun counterDelta(after: Long?, before: Long?): Long? =
    if (after != null && before != null && after >= before) after - before else null

/**
 * Shares the input lane, but never writes rule state, performs input or clears framework caches.
 */
suspend fun benchmarkAndroidMatching(input: AndroidBenchmarkRequest): AndroidBenchmarkReport {
    require(!input.request.execute && input.request.simulation == null) {
        "Android benchmark only reads live nodes"
    }
    require(input.request.subscription.length <= 1_000_000)
    require(input.iterations in 1..50 && input.warmup in 0..10)
    require(
        input.cacheModes.isNotEmpty() && input.cacheModes.distinct().size == input.cacheModes.size,
    )
    check(!SettingsRepository.settings.value.enableMatch) {
        "Disable rule matching before benchmarking"
    }
    check(!MatchingRuntime.coordinator.diagnostics.enabled) {
        "Stop live diagnostics before benchmarking"
    }
    check(isInteractive && !ScreenUtils.isLocked()) { "Unlock the screen before benchmarking" }
    val service =
        checkNotNull(AndroidAutomationSessions.service) { "Accessibility service is unavailable" }
    check(
        AndroidAutomationSessions.isEffective(service) && !AndroidForegroundState.foregroundPending,
    ) {
        "Wait for a confirmed foreground and effective service"
    }
    val page = AndroidForegroundState.currentPage
    val activity = AndroidForegroundState.currentRule.topActivity
    val apps = checkNotNull(AppInfoRepository.snapshot) { "Wait for the app catalog to load" }
    val rule = prepareDebugRule(input.request, apps.apps)
    require(rule.matchDelay == 0L) { "Benchmark requires matchDelay=0" }
    check(androidBenchmarkLock.tryLock()) { "An Android benchmark is already running" }
    try {
        return withTimeout(120_000) {
            MatchingRuntime.coordinator.execute(service) {
                val lane = this
                withContext(Dispatchers.IO) {
                    suspend fun checkCurrent() {
                        lane.ensureCurrent()
                        check(!SettingsRepository.settings.value.enableMatch) {
                            "Rule matching was enabled during benchmark"
                        }
                        check(
                            AndroidAutomationSessions.service === service &&
                                AndroidForegroundState.currentPage === page &&
                                !AndroidForegroundState.foregroundPending &&
                                isInteractive &&
                                !ScreenUtils.isLocked(),
                        ) {
                            "Benchmark foreground changed"
                        }
                    }
                    checkCurrent()
                    val baselineRoot =
                        checkNotNull(service.windowNodeInfo) { "Root node unavailable" }
                    check(baselineRoot.packageName?.toString() == activity.appId) {
                        "Root and foreground disagree"
                    }
                    val childCount = baselineRoot.childCount
                    val job = checkNotNull(currentCoroutineContext()[Job])
                    class Case(val cache: AndroidBenchmarkCache, val level: String) {
                        private val tracePrefixes =
                            listOf("warmup", "measured").map { "GKD.benchmark.$cache.$level.$it" }
                        private val rootTraceNames = tracePrefixes.map { "$it.root" }
                        private val selectorTraceNames = tracePrefixes.map { "$it.selector" }
                        private val refreshTraceNames = tracePrefixes.map { "$it.refresh" }
                        private var tracePhase = 0
                        val metrics = MatchNodeMetrics()
                        val diagnostics =
                            MatchDiagnostics().also {
                                if (level != "off")
                                    it.start(
                                        lane.policy,
                                        detailed = level == "detailed",
                                        ruleFilter = rule.matchIdentity(),
                                        recordQueries = true,
                                        recordSystem = true,
                                    )
                            }
                        val reader = MatchNodeReader(diagnostics)
                        val work =
                            MatchWork(lane.id, lane.policy, diagnostics) {
                                AndroidForegroundState.currentPage === page
                            }
                        val measurements = mutableListOf<AndroidBenchmarkMeasurement>()
                        val readRoot = {
                            traceRead(rootTraceNames[tracePhase]) {
                                metrics
                                    .measure(NodeReadOperation.Root) {
                                        service.windowNodeInfo?.setGeneratedTime()
                                    }
                                    ?.also { node ->
                                        if (cache == AndroidBenchmarkCache.RefreshRoot) {
                                            check(
                                                metrics.measure(NodeReadOperation.Refresh) {
                                                    node.refresh()
                                                },
                                            ) {
                                                "Root refresh failed"
                                            }
                                            node.setGeneratedTime()
                                        }
                                        check(
                                            node == baselineRoot &&
                                                node.packageName == baselineRoot.packageName &&
                                                node.childCount == childCount,
                                        ) {
                                            "Root changed during benchmark"
                                        }
                                    }
                            }
                        }

                        fun context() =
                            A11yQueryContext(
                                    getRoot = readRoot,
                                    interruptable = false,
                                    metrics = metrics,
                                    nodeReader = reader,
                                    diagnostics = diagnostics,
                                )
                                .also {
                                    it.queryJob = job
                                    it.queryRunId = lane.id
                                }

                        val warmContext =
                            if (cache == AndroidBenchmarkCache.Warm) context() else null
                        var target: AccessibilityNodeInfo? = null

                        suspend fun query(measured: Boolean): MatchDebugResult {
                            tracePhase = if (measured) 1 else 0
                            val context = warmContext ?: context()
                            target = null
                            return evaluateDebugRule(
                                work,
                                input.request,
                                rule,
                                activity.appId,
                                activity.activityId,
                                object : RuleQueryNodes<AccessibilityNodeInfo> {
                                    override suspend fun root() = readRoot()

                                    override fun appId(node: AccessibilityNodeInfo) =
                                        node.packageName?.toString()

                                    override suspend fun refresh(node: AccessibilityNodeInfo) =
                                        traceRead(refreshTraceNames[tracePhase]) {
                                            metrics.measure(NodeReadOperation.Refresh) {
                                                node.takeIf { it.refresh() }
                                            }
                                        }

                                    override suspend fun match(
                                        rule: ResolvedRule,
                                        node: AccessibilityNodeInfo,
                                    ) = reader.query {
                                        traceRead(selectorTraceNames[tracePhase]) {
                                            context.queryRule(rule, node, diagnostics).also {
                                                target = it
                                            }
                                        }
                                    }

                                    override suspend fun perform(
                                        rule: ResolvedRule,
                                        node: AccessibilityNodeInfo,
                                    ): ActionResult = error("Benchmark cannot perform input")
                                },
                            )
                        }
                    }
                    val cases =
                        input.cacheModes.flatMap { cache ->
                            listOf("off", "flow", "detailed").map { Case(cache, it) }
                        }
                    var expected: MatchDebugResult? = null
                    var expectedTarget: AccessibilityNodeInfo? = null
                    suspend fun query(case: Case, measured: Boolean = false) {
                        checkCurrent()
                        val beforeNodes = if (measured) case.metrics.snapshot() else null
                        val before = if (measured) ProcessCounters.read() else null
                        // queryRules has suspension points; section begin/end must stay on one
                        // thread. Individual synchronous node/selector calls are traced below.
                        val started = System.nanoTime()
                        val result = case.query(measured)
                        val elapsed = System.nanoTime() - started
                        val after = if (measured) ProcessCounters.read() else null
                        checkCurrent()
                        check(
                            expected == null ||
                                (expected == result && expectedTarget == case.target),
                        ) {
                            "Query target or eligibility changed; use a stable screen and rule"
                        }
                        expected = result
                        expectedTarget = case.target
                        if (before != null && after != null && beforeNodes != null) {
                            case.measurements +=
                                AndroidBenchmarkMeasurement(
                                    elapsed,
                                    counterDelta(after.sent, before.sent),
                                    counterDelta(after.received, before.received),
                                    counterDelta(after.allocated, before.allocated),
                                    case.metrics.snapshot() - beforeNodes,
                                )
                        }
                    }
                    try {
                        // Prime every case even for warmup=0; measured warm cases always have prior
                        // reads.
                        repeat(input.warmup.coerceAtLeast(1)) { round ->
                            repeat(cases.size) { offset ->
                                query(cases[(round + offset) % cases.size])
                            }
                        }
                        val idleBefore = ProcessCounters.read()
                        delay(50)
                        val idleAfter = ProcessCounters.read()
                        repeat(input.iterations) { round ->
                            repeat(cases.size) { offset ->
                                query(cases[(round + offset) % cases.size], measured = true)
                            }
                        }
                        val result = checkNotNull(expected)
                        AndroidBenchmarkReport(
                            Build.VERSION.SDK_INT,
                            Build.MODEL,
                            service.mode.value,
                            activity.appId,
                            baselineRoot.windowId,
                            result.matched,
                            result.reason,
                            input.request.rule,
                            input.warmup.coerceAtLeast(1),
                            50,
                            counterDelta(idleAfter.sent, idleBefore.sent),
                            counterDelta(idleAfter.received, idleBefore.received),
                            cases.map { case ->
                                val ordered = case.measurements.map { it.nanos }.sorted()
                                AndroidBenchmarkSample(
                                    case.cache,
                                    case.level,
                                    ordered[ordered.size / 2],
                                    ordered[(ordered.size * 95 + 99) / 100 - 1],
                                    case.measurements,
                                )
                            },
                        )
                    } finally {
                        cases.forEach { it.reader.close() }
                    }
                }
            }
        }
    } finally {
        androidBenchmarkLock.unlock()
    }
}
