package li.gkd.app.development.matching

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchEngineSnapshot
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchDebugResult
import li.gkd.app.matching.diagnostics.MatchDebugRunPhase
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest
import li.gkd.app.matching.diagnostics.MatchDebugRunState
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.MatchSystemRecorder
import li.gkd.app.matching.query.RuleQueryNodes
import li.gkd.app.matching.query.queryRules
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.AppRule
import li.gkd.app.rule.GlobalRule
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

expect suspend fun runMatchDebug(
    request: MatchDebugRunRequest,
    onStarted: (Long) -> Unit,
): MatchDebugResult

object MatchDebugRuns {
    private val lock = Any()
    private var sessionId: String? = null
    private val runs = linkedMapOf<String, MatchDebugRunState>()
    private val jobs = mutableMapOf<String, Job>()

    private fun update(id: String, value: MatchDebugRunState) {
        runs[value.request.requestId] = value
        MatchingRuntime.coordinator.diagnostics.recordRequest(id, value)
    }

    fun start(id: String, request: MatchDebugRunRequest): MatchDebugRunState {
        val scope = MatchingRuntime.coordinator.diagnosticScope(id)
        return synchronized(lock) {
            require(request.requestId.matches(Regex("[A-Za-z0-9-]{1,64}")))
            require(request.subscription.length <= 1_000_000)
            if (sessionId != id) {
                check(jobs.isEmpty()) { "Previous diagnostic input is still stopping" }
                sessionId = id
                runs.clear()
            }
            runs[request.requestId]?.let {
                require(Json.encodeToJsonElement(it.request) == Json.encodeToJsonElement(request)) {
                    "Request ID already has a different payload"
                }
                return@synchronized it
            }
            check(runs.size < 100) { "Diagnostic run limit reached" }
            val initial = MatchDebugRunState(request, MatchDebugRunPhase.Running)
            update(id, initial)
            val job =
                scope.launch(start = CoroutineStart.LAZY) {
                    val result =
                        try {
                            MatchDebugRunState(
                                request,
                                MatchDebugRunPhase.Completed,
                                runMatchDebug(request) { engineRunId ->
                                    synchronized(lock) {
                                        update(
                                            id,
                                            runs
                                                .getValue(request.requestId)
                                                .copy(engineRunId = engineRunId),
                                        )
                                    }
                                },
                            )
                        } catch (error: CancellationException) {
                            MatchDebugRunState(request, MatchDebugRunPhase.Cancelled)
                        } catch (error: Exception) {
                            MatchDebugRunState(
                                request,
                                MatchDebugRunPhase.Failed,
                                error = error.toString(),
                            )
                        }
                    synchronized(lock) {
                        update(
                            id,
                            result.copy(engineRunId = runs.getValue(request.requestId).engineRunId),
                        )
                    }
                }
            jobs[request.requestId] = job
            job.invokeOnCompletion { error ->
                synchronized(lock) {
                    jobs.remove(request.requestId)
                    // A session may stop before a lazy request enters its body.
                    val current = runs.getValue(request.requestId)
                    if (
                        error is CancellationException &&
                            (current.phase == MatchDebugRunPhase.Running ||
                                current.phase == MatchDebugRunPhase.Cancelling)
                    ) {
                        update(id, current.copy(phase = MatchDebugRunPhase.Cancelled))
                    }
                }
            }
            job.start()
            runs.getValue(request.requestId)
        }
    }

    fun get(id: String, runId: String): MatchDebugRunState {
        return requireNotNull(snapshot(id).find { it.request.requestId == runId }) {
            "Unknown diagnostic request"
        }
    }

    fun cancel(id: String, runId: String): MatchDebugRunState =
        synchronized(lock) {
            require(sessionId == id)
            val current = requireNotNull(runs[runId])
            jobs[runId]?.let {
                update(id, current.copy(phase = MatchDebugRunPhase.Cancelling))
                it.cancel()
            }
            checkNotNull(runs[runId])
        }

    fun snapshot(id: String): List<MatchDebugRunState> {
        val current = MatchingRuntime.coordinator.diagnostics.requests(id)
        val engine = MatchingRuntime.coordinator.snapshot()
        return current.map { it.observedPhase(engine) }
    }

    // Keep engine access outside the request lock; coroutine completion also updates these
    // requests.
    private fun MatchDebugRunState.observedPhase(engine: MatchEngineSnapshot) =
        if (
            phase == MatchDebugRunPhase.Running &&
                engineRunId != null &&
                engineRunId == engine.activeRunId &&
                engine.cancelling
        )
            copy(phase = MatchDebugRunPhase.Cancelling)
        else this
}

fun prepareDebugRule(
    request: MatchDebugRunRequest,
    apps: Map<String, AppInfo>,
    recording: () -> MatchSystemRecorder? = { null },
): ResolvedRule {
    val raw = RawSubscriptionParser.parse(request.subscription)
    val runtime = RuleRuntime(observer = recording)
    val summary =
        RuleSummaryBuilder.build(
            runtime,
            listOf(UsedSubsEntry(SubsItem(raw.id, enable = true, order = 0), raw)),
            apps,
            emptyList(),
            emptyList(),
            emptyList(),
        )
    val rules = summary.globalRules + summary.appIdToRules.values.flatten()
    val now = runtime.now()
    rules.forEach { it.resetState(now) }
    return rules.singleOrNull {
        it.matchIdentity() == request.rule
    } ?: error("Rule is missing, disabled, or incompatible with the supplied app version")
}

suspend fun <N : Any> evaluateDebugRule(
    work: MatchWork,
    request: MatchDebugRunRequest,
    rule: ResolvedRule,
    appId: String,
    activityId: String?,
    nodes: RuleQueryNodes<N>,
): MatchDebugResult {
    if (!rule.matchActivity(appId, activityId)) {
        work.trace(MatchStage.Rule, "activity_mismatch", rule)
        return MatchDebugResult(false, "activity_mismatch")
    }
    val summary =
        when (rule) {
            is AppRule -> RuleSummary(appIdToRules = mapOf(appId to listOf(rule)))
            is GlobalRule -> RuleSummary(globalRules = listOf(rule))
        }
    val activity = ForegroundRuleSnapshot(TopActivity(appId, activityId), false, summary)
    var result = MatchDebugResult(false, "not_matched")
    do {
        var nextDelay: Long? = null
        queryRules(
            work = work,
            activity = activity,
            nodes = nodes,
            source = null,
            forced = false,
            isCurrent = { true },
            schedule = { _, _, millis -> nextDelay = millis },
            correctApp = {},
            completed = { _, _, _ -> },
            execute = request.execute,
            observed = { _, reason, matched, action ->
                result = MatchDebugResult(matched, reason, action)
            },
        )
        val wait = nextDelay ?: break
        delay(wait)
        work.ensureCurrent()
    } while (true)
    return result
}
