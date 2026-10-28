package li.gkd.app.matching.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import li.gkd.app.util.monotonicTimeMillis

@Serializable
enum class MatchEventKind {
    Content,
    WindowState,
}

@Serializable
data class MatchEventKey(val kind: MatchEventKind, val appId: String, val name: String)

@Serializable
data class MatchEventContext(
    val interactive: Boolean,
    val ownAppId: String,
    val ownVisible: Boolean,
    val imeAppId: String,
    val confirmedAppId: String,
    val pageAgeMillis: Long,
    val actionAgeMillis: Long,
)

@Serializable
sealed interface MatchInput {
    @Serializable
    @SerialName("system_surface")
    data class SystemSurface(val covered: Boolean?) : MatchInput

    @Serializable
    @SerialName("foreground")
    data class Foreground(
        val source: ForegroundSource,
        val identity: ForegroundIdentity,
    ) : MatchInput

    @Serializable
    @SerialName("accessibility")
    data class Accessibility(
        val key: MatchEventKey,
        val context: MatchEventContext,
        val primaryDisplay: Boolean = true,
        val recordCount: Int = 0,
        val action: Int = 0,
        val fullScreen: Boolean = false,
    ) : MatchInput

    @Serializable @SerialName("service_changed") data object ServiceChanged : MatchInput

    @Serializable
    @SerialName("screen")
    data class Screen(val interactive: Boolean, val forceInvalidate: Boolean = false) : MatchInput
}

@Serializable
data class MatchInputRecord(
    val sequence: Long,
    val receivedAtMillis: Long,
    val sourceTimeMillis: Long?,
    val policy: MatchTimingPolicy,
    val input: MatchInput,
)

@Serializable
data class MatchInputDecision(
    val accepted: Boolean,
    val reason: String,
    val foreground: ForegroundDecision? = null,
    val cancelWork: Boolean = false,
)

@Serializable
data class MatchInputCheckpoint(
    val sequence: Long,
    val receivedAtMillis: Long,
    val foreground: ForegroundCheckpoint,
    val lastContentAt: Long? = null,
    val lastEventSourceTime: Long? = null,
)

/** Production input decisions; snapshots describe state but cannot restore execution. */
class MatchInputReducer(initial: ForegroundIdentity) {
    private val foreground = ForegroundTracker(initial)
    private var lastContentAt: Long? = null
    private var lastEventSourceTime: Long? = null

    fun checkpoint(sequence: Long, receivedAtMillis: Long) =
        MatchInputCheckpoint(
            sequence,
            receivedAtMillis,
            foreground.checkpoint(),
            lastContentAt,
            lastEventSourceTime,
        )

    fun foregroundSnapshot() = foreground.snapshot()

    fun accept(
        input: MatchInput,
        policy: MatchTimingPolicy,
        receivedAtMillis: Long,
        sourceTimeMillis: Long?,
    ): MatchInputDecision {
        return when (input) {
            is MatchInput.SystemSurface -> {
                val decision = foreground.observeSurface(input.covered, receivedAtMillis)
                MatchInputDecision(
                    true,
                    decision.reason.wireValue,
                    decision,
                    cancelWork =
                        decision.reason == ForegroundDecisionReason.SystemSurfaceEntered ||
                            (decision.reason == ForegroundDecisionReason.SystemSurfaceExited &&
                                decision.pending != null),
                )
            }
            is MatchInput.Foreground -> {
                val previousPending = foreground.snapshot().pending
                val decision =
                    foreground.observe(
                        input.source,
                        input.identity,
                        policy,
                        receivedAtMillis,
                    )
                MatchInputDecision(
                    true,
                    decision.reason.wireValue,
                    decision,
                    cancelWork =
                        decision.changed ||
                            decision.appReentered ||
                            (decision.pending != null && previousPending != decision.pending),
                )
            }
            MatchInput.ServiceChanged -> {
                lastContentAt = null
                lastEventSourceTime = null
                MatchInputDecision(true, "service_changed", cancelWork = true)
            }
            is MatchInput.Screen ->
                MatchInputDecision(
                    true,
                    if (input.interactive) "screen_on" else "screen_off",
                    cancelWork = !input.interactive || input.forceInvalidate,
                )
            is MatchInput.Accessibility -> {
                val context = input.context
                val reason =
                    acceptEvent(
                        input.key,
                        context.interactive,
                        context.ownAppId,
                        context.ownVisible,
                        context.imeAppId,
                        context.confirmedAppId,
                        context.pageAgeMillis,
                        context.actionAgeMillis,
                        input.primaryDisplay,
                        input.recordCount,
                        input.action,
                        input.fullScreen,
                        policy,
                        receivedAtMillis,
                        sourceTimeMillis,
                    )
                MatchInputDecision(reason == MatchEventDecision.EventAccepted, reason.wireValue)
            }
        }
    }

    fun acceptEvent(
        key: MatchEventKey,
        interactive: Boolean,
        ownAppId: String,
        ownVisible: Boolean,
        imeAppId: String,
        confirmedAppId: String,
        pageAgeMillis: Long,
        actionAgeMillis: Long,
        primaryDisplay: Boolean,
        recordCount: Int,
        action: Int,
        fullScreen: Boolean,
        policy: MatchTimingPolicy,
        receivedAtMillis: Long,
        sourceTimeMillis: Long?,
    ): MatchEventDecision {
        if (!primaryDisplay) return MatchEventDecision.SecondaryDisplay
        if (key.kind == MatchEventKind.Content && !interactive)
            return MatchEventDecision.ScreenOffContent
        if (
            key.appId == imeAppId &&
                confirmedAppId != imeAppId &&
                recordCount == 0 &&
                action == 0 &&
                !fullScreen
        )
            return MatchEventDecision.InputMethodEvent
        if (key.appId == ownAppId && (key.kind == MatchEventKind.Content || !ownVisible)) {
            return MatchEventDecision.OwnAppEvent
        }
        if (key.kind == MatchEventKind.Content) {
            if (
                lastContentAt?.let {
                    receivedAtMillis - it < policy.contentMergeMillis
                } == true &&
                    pageAgeMillis > policy.pageSettleMillis &&
                    actionAgeMillis > policy.followUpWindowMillis
            )
                return MatchEventDecision.ContentMerged
            lastContentAt = receivedAtMillis
        }
        // Only accessibility source timestamps share this clock domain.
        val sourceTime = sourceTimeMillis
        if (sourceTime != null) {
            if (sourceTime < 0 || lastEventSourceTime?.let { sourceTime < it } == true)
                return MatchEventDecision.StaleAccessibilityEvent
            lastEventSourceTime = sourceTime
        }
        return MatchEventDecision.EventAccepted
    }
}

/** Serializes reception. The optional checkpoint is constructed only during diagnostics. */
class MatchInputStream(
    initial: ForegroundIdentity,
    private val nowMillis: () -> Long = ::monotonicTimeMillis,
    private val capture: () -> Boolean = { false },
    private val record: (MatchInputCheckpoint, MatchInputRecord, MatchInputDecision) -> Unit =
        { _, _, _ ->
        },
) {
    private val reducer = MatchInputReducer(initial)
    private var sequence = 0L
    private var receivedAt = 0L
    private val origin = nowMillis()

    @Synchronized
    fun accept(
        input: MatchInput,
        policy: MatchTimingPolicy,
        sourceTimeMillis: Long? = null,
    ): MatchInputDecision {
        val before = if (capture()) reducer.checkpoint(sequence, receivedAt) else null
        val now = nowMillis() - origin
        val decision = reducer.accept(input, policy, now, sourceTimeMillis)
        sequence++
        receivedAt = now
        if (before != null)
            record(
                before,
                MatchInputRecord(sequence, now, sourceTimeMillis, policy, input),
                decision,
            )
        return decision
    }

    /** High-frequency platform events need their diagnostic wrappers only while capturing. */
    @Synchronized
    fun acceptAccessibility(
        key: MatchEventKey,
        interactive: Boolean,
        ownAppId: String,
        ownVisible: Boolean,
        imeAppId: String,
        confirmedAppId: String,
        pageAgeMillis: Long,
        actionAgeMillis: Long,
        primaryDisplay: Boolean,
        recordCount: Int,
        action: Int,
        fullScreen: Boolean,
        policy: MatchTimingPolicy,
        sourceTimeMillis: Long?,
    ): Boolean {
        val before = if (capture()) reducer.checkpoint(sequence, receivedAt) else null
        val now = nowMillis() - origin
        val reason =
            reducer.acceptEvent(
                key,
                interactive,
                ownAppId,
                ownVisible,
                imeAppId,
                confirmedAppId,
                pageAgeMillis,
                actionAgeMillis,
                primaryDisplay,
                recordCount,
                action,
                fullScreen,
                policy,
                now,
                sourceTimeMillis,
            )
        val accepted = reason == MatchEventDecision.EventAccepted
        sequence++
        receivedAt = now
        if (before != null) {
            val input =
                MatchInput.Accessibility(
                    key,
                    MatchEventContext(
                        interactive,
                        ownAppId,
                        ownVisible,
                        imeAppId,
                        confirmedAppId,
                        pageAgeMillis,
                        actionAgeMillis,
                    ),
                    primaryDisplay,
                    recordCount,
                    action,
                    fullScreen,
                )
            record(
                before,
                MatchInputRecord(sequence, now, sourceTimeMillis, policy, input),
                MatchInputDecision(accepted, reason.wireValue),
            )
        }
        return accepted
    }

    @Synchronized fun foregroundSnapshot() = reducer.foregroundSnapshot()
}
