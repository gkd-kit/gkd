package li.gkd.app.matching.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.appScope
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchInvalidationReason
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.RuleAvailability
import li.gkd.app.matching.RuleExecutionHost
import li.gkd.app.matching.diagnostics.MatchPageObservation
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.retainUnaffectedExecution
import li.gkd.app.record.AppVisitInput
import li.gkd.app.record.RuntimeRecordRepository
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.TopActivity
import li.gkd.app.service.updateTopTaskAppId
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.util.Constants
import li.gkd.app.util.launchLogged
import li.songe.codeorigin.CallSite

val foregroundRulesFlow: StateFlow<ForegroundRuleSnapshot>
    get() = AndroidForegroundState.foregroundRulesFlow

val topActivityFlow = foregroundRulesFlow.map { it.topActivity }.distinctUntilChanged()
val currentTopActivity: TopActivity
    get() = foregroundRulesFlow.value.topActivity

sealed class ActivityScene {
    data object ScreenOn : ActivityScene()

    data object A11y : ActivityScene()

    data object TaskStack : ActivityScene()

    data object Nodes : ActivityScene()
}

object AndroidForegroundState {
    private val lock = Any()

    // Only state reads/commits belong here. Platform Binder queries must run outside this lock.
    fun <T> withTopActivityLock(block: () -> T): T = synchronized(lock, block)

    val foregroundRulesFlow: StateFlow<ForegroundRuleSnapshot>
        field =
            MutableStateFlow(
                ForegroundRuleSnapshot(
                    blockMatch = SettingsRepository.checkAppBlockMatch(Constants.systemUiAppId),
                    ruleAvailability = RuleAvailability.Loading,
                ),
            )
    val currentRule: ForegroundRuleSnapshot
        get() = synchronized(lock) { foregroundRulesFlow.value }

    private var page = Any()
    private var confirmedPage: Any? = null
    val currentPage: Any
        get() = synchronized(lock) { page }

    fun onWindowChanged(): Unit =
        synchronized(lock) {
            page = Any()
            MatchingRuntime.coordinator.invalidate(MatchInvalidationReason.ForegroundConflict)
        }

    fun onRulesChanged(snapshot: Loadable<RuleSummary>) {
        synchronized(lock) {
            if (SubscriptionState.ruleSummaryFlow.value !== snapshot) return
            val summary = snapshot.value ?: RuleSummary()
            if (currentRule.ruleSummary === summary) return
            val top = currentRule.topActivity
            applyTopActivity(
                top.appId,
                top.activityId,
                ActivityScene.A11y,
                "rules_updated",
                snapshot,
                true,
            )
        }
        AndroidAutomationSessions.service?.matchHost?.onRulesChanged()
    }

    fun onScreenForcedActive(): Unit =
        synchronized(lock) {
            val top = foregroundRulesFlow.value.topActivity
            updateTopActivity(top.appId, top.activityId, ActivityScene.ScreenOn)
        }

    private var lastValidActivity: TopActivity = foregroundRulesFlow.value.topActivity
        set(value) {
            if (value.activityId != null) {
                field = value
            }
        }

    @Volatile
    var foregroundPending = false
        private set

    fun foregroundSnapshot() = synchronized(lock) { MatchingRuntime.inputs.foregroundSnapshot() }

    fun updateSystemSurface(covered: Boolean?, sourceTimeMillis: Long? = null): Unit =
        synchronized(lock) {
            val result =
                MatchingRuntime.inputs.accept(
                    MatchInput.SystemSurface(covered),
                    MatchingRuntime.coordinator.policy.value,
                    sourceTimeMillis,
                )
            val decision = checkNotNull(result.foreground)
            foregroundPending = decision.pending != null
            if (result.cancelWork) {
                page = Any()
                // Same soft invalidation as a page change: an already-started gesture completes.
                MatchingRuntime.coordinator.invalidate(MatchInvalidationReason.ForegroundConflict)
            }
            if (decision.reason != ForegroundDecisionReason.SurfaceUnchanged)
                AndroidAutomationSessions.service?.matchHost?.confirmForeground()
        }

    private var lastAppId = Constants.systemUiAppId

    fun updateTopActivity(
        appId: String,
        activityId: String?,
        scene: ActivityScene = ActivityScene.A11y,
        sourceTimeMillis: Long? = null,
        @CallSite loc: String = "",
    ): Unit =
        synchronized(lock) {
            if (scene == ActivityScene.TaskStack) {
                updateTopTaskAppId(appId)
            }
            if (scene != ActivityScene.ScreenOn) {
                val source =
                    when (scene) {
                        ActivityScene.TaskStack -> ForegroundSource.TaskStack
                        ActivityScene.Nodes -> ForegroundSource.Nodes
                        else -> ForegroundSource.Accessibility
                    }
                val inputDecision =
                    MatchingRuntime.inputs.accept(
                        MatchInput.Foreground(source, ForegroundIdentity(appId, activityId)),
                        MatchingRuntime.coordinator.policy.value,
                        sourceTimeMillis,
                    )
                val decision = checkNotNull(inputDecision.foreground)
                foregroundPending = decision.pending != null
                if (foregroundPending) {
                    if (inputDecision.cancelWork) {
                        page = Any()
                        MatchingRuntime.coordinator.invalidate(
                            MatchInvalidationReason.ForegroundConflict,
                        )
                    }
                    AndroidAutomationSessions.service?.matchHost?.confirmForeground()
                    return@synchronized
                }
                if (
                    !decision.changed &&
                        !decision.appReentered &&
                        (scene == ActivityScene.Nodes ||
                            scene == ActivityScene.TaskStack ||
                            decision.reason == ForegroundDecisionReason.TaskBelowConfirmedSurface ||
                            decision.reason == ForegroundDecisionReason.TaskBelowSystemSurface ||
                            decision.reason == ForegroundDecisionReason.ExitingSystemSurface)
                )
                    return@synchronized
                applyTopActivity(
                    decision.confirmed.appId,
                    decision.confirmed.activityId,
                    scene,
                    loc,
                    appReentered = decision.appReentered,
                )
                return@synchronized
            }
            applyTopActivity(appId, activityId, scene, loc)
        }

    private fun applyTopActivity(
        appId: String,
        activityId: String?,
        scene: ActivityScene,
        loc: String,
        snapshot: Loadable<RuleSummary> = SubscriptionState.ruleSummaryFlow.value,
        rulesOnly: Boolean = false,
        appReentered: Boolean = false,
    ) {
        val ruleSummary = snapshot.value ?: RuleSummary()
        val availability =
            when (snapshot) {
                Loadable.Loading -> RuleAvailability.Loading
                is Loadable.Ready -> RuleAvailability.Ready
                is Loadable.Failure -> RuleAvailability.Failed
            }
        val wallTime = System.currentTimeMillis()
        val t = RuleExecutionHost.runtime.now()
        val oldActivity = foregroundRulesFlow.value.topActivity
        val oldActivityRule = foregroundRulesFlow.value
        val idChanged =
            (appReentered ||
                scene == ActivityScene.ScreenOn ||
                appId != oldActivityRule.topActivity.appId)
        val isSame =
            !appReentered &&
                scene != ActivityScene.ScreenOn &&
                oldActivity.sameAs(appId, activityId)
        val number =
            if (isSame) {
                oldActivity.number + 1
            } else {
                0
            }
        val topActivity =
            if (rulesOnly) oldActivity
            else
                TopActivity(
                    appId = appId,
                    activityId =
                        activityId ?: lastValidActivity.takeIf { it.appId == appId }?.activityId,
                    number = number,
                )
        lastValidActivity = oldActivity
        if (!rulesOnly)
            appScope.launchLogged {
                RuntimeRecordRepository.recordActivity(appId, activityId, wallTime)
            }
        // Keep foreground/visit bookkeeping active while no complete executable snapshot exists.
        // Presentation still observes Loading/Failure; the executor receives no runnable rules.
        val topChanged = idChanged || oldActivityRule.topActivity != topActivity
        val ruleChanged = oldActivityRule.ruleSummary !== ruleSummary
        if (topChanged || ruleChanged) {
            if (ruleChanged) ruleSummary.retainUnaffectedExecution(oldActivityRule.ruleSummary)
            if (!isSame) {
                page = Any()
                MatchingRuntime.coordinator.invalidate(MatchInvalidationReason.PageChanged)
            }
            val newActivityRule =
                ForegroundRuleSnapshot(
                    ruleSummary = ruleSummary,
                    ruleAvailability = availability,
                    topActivity = topActivity,
                    blockMatch = SettingsRepository.checkAppBlockMatch(topActivity.appId),
                )
            if (idChanged) {
                val oldAppId = lastAppId
                lastAppId = appId
                val visit = AppVisitInput(oldAppId, appId, wallTime)
                appScope.launchLogged { RuntimeRecordRepository.recordVisit(visit) }
                RuleExecutionHost.runtime.onAppChanged(t)
                ruleSummary.globalRules.forEach { it.resetState(t) }
                ruleSummary.appIdToRules[oldActivityRule.topActivity.appId]?.forEach {
                    it.resetState(
                        t,
                    )
                }
                newActivityRule.appRules.forEach { it.resetState(t) }
            } else {
                newActivityRule.currentRules.forEach { r ->
                    val retained = oldActivityRule.currentRules.any { it.sameExecution(r) }
                    if (!isSame || !ruleChanged || !retained) {
                        r.onActivityTransition(t, previouslyMatched = retained)
                    }
                }
            }
            foregroundRulesFlow.value = newActivityRule
            val firstConfirmation = !rulesOnly && confirmedPage !== page
            if (!rulesOnly) confirmedPage = page
            if (MatchingRuntime.coordinator.diagnostics.enabled) {
                MatchingRuntime.coordinator.diagnostics.record(
                    0,
                    MatchStage.Root,
                    "page_confirmed",
                    pageObservation =
                        MatchPageObservation(
                            System.identityHashCode(page),
                            topActivity.appId,
                            topActivity.activityId,
                            firstConfirmation = firstConfirmation,
                        ),
                )
            }
            MatchingRuntime.coordinator.updateRules(newActivityRule)
            LogUtils.i(
                "${oldActivity.format()} -> ${topActivity.format()} " +
                    "(scene=$scene, rules=${newActivityRule.currentRules.size}" +
                    (if (newActivityRule.currentRules.isEmpty())
                        ", reason=${newActivityRule.emptyReason}"
                    else "") +
                    ")",
                loc = loc,
                tag = "updateTopActivity",
            )
        }
    }
}

fun updateTopActivity(
    appId: String,
    activityId: String?,
    scene: ActivityScene = ActivityScene.A11y,
    sourceTimeMillis: Long? = null,
    @CallSite loc: String = "",
) = AndroidForegroundState.updateTopActivity(appId, activityId, scene, sourceTimeMillis, loc)

val lastTriggerTime: Long
    get() = RuleExecutionHost.runtime.lastTriggerTime

val appChangeTime: Long
    get() = RuleExecutionHost.runtime.appChangeTime
