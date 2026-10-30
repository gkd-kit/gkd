package li.gkd.app.matching.input

import kotlinx.serialization.Serializable
import li.gkd.app.util.monotonicTimeMillis

@Serializable
enum class ForegroundSource {
    TaskStack,
    Accessibility,
    Nodes,
}

@Serializable data class ForegroundIdentity(val appId: String, val activityId: String? = null)

@Serializable
data class ForegroundDecision(
    val confirmed: ForegroundIdentity,
    val pending: ForegroundIdentity?,
    val reason: ForegroundDecisionReason,
    val changed: Boolean,
    val task: ForegroundIdentity? = null,
    val nodeAppId: String? = null,
    val appReentered: Boolean = false,
)

@Serializable
data class ForegroundCheckpoint(
    val confirmed: ForegroundIdentity,
    val pending: ForegroundIdentity?,
    val pendingSince: Long,
    val pendingSource: ForegroundSource,
    val lastNodes: String?,
    val lastNodesAt: Long?,
    val task: ForegroundIdentity?,
    val reason: ForegroundDecisionReason,
    val pendingAppReentry: Boolean = false,
    val surfaceCovered: Boolean? = null,
    val surfaceReturn: ForegroundIdentity? = null,
)

/**
 * Source order is not authority. A new task/window waits for node corroboration; repeated
 * independent node observations can confirm when callbacks lag behind.
 */
class ForegroundTracker(
    initial: ForegroundIdentity,
    private val nowMillis: () -> Long = ::monotonicTimeMillis,
) {
    private var confirmed = initial
    private var pending: ForegroundIdentity? = null
    private var pendingSince = 0L
    private var pendingSource = ForegroundSource.Nodes
    private var lastNodes: String? = null
    private var lastNodesAt: Long? = null
    private var task: ForegroundIdentity? = null
    private var reason = ForegroundDecisionReason.Initial
    private var pendingAppReentry = false
    private var surfaceCovered: Boolean? = null
    private var surfaceReturn: ForegroundIdentity? = null

    @Synchronized
    fun checkpoint() =
        ForegroundCheckpoint(
            confirmed,
            pending,
            pendingSince,
            pendingSource,
            lastNodes,
            lastNodesAt,
            task,
            reason,
            pendingAppReentry,
            surfaceCovered,
            surfaceReturn,
        )

    @Synchronized
    fun observeSurface(covered: Boolean?, now: Long): ForegroundDecision {
        if (surfaceCovered == covered) return decision(ForegroundDecisionReason.SurfaceUnchanged)
        val previous = surfaceCovered
        surfaceCovered = covered
        if (covered == null) return decision(ForegroundDecisionReason.SurfaceUnavailable)
        if (covered) {
            surfaceReturn =
                task?.takeIf { it.appId != SYSTEM_UI } ?: confirmed.takeIf { it.appId != SYSTEM_UI }
            if (confirmed.appId != SYSTEM_UI) {
                pending = ForegroundIdentity(SYSTEM_UI)
                pendingSince = now
                pendingSource = ForegroundSource.Accessibility
            } else pending = null
            return decision(ForegroundDecisionReason.SystemSurfaceEntered)
        }
        val returning = task?.takeIf { it.appId != SYSTEM_UI } ?: surfaceReturn
        val exited = previous == true || surfaceReturn != null
        if (exited && returning != null) {
            pendingAppReentry = confirmed.appId == returning.appId
            pending = returning
            pendingSince = now
            pendingSource = ForegroundSource.Accessibility
        }
        surfaceReturn = null
        return decision(
            if (exited) ForegroundDecisionReason.SystemSurfaceExited
            else ForegroundDecisionReason.SurfaceObservedClear,
        )
    }

    @Synchronized
    fun snapshot(): ForegroundDecision =
        ForegroundDecision(confirmed, pending, reason, false, task, lastNodes)

    private fun decision(
        reason: ForegroundDecisionReason,
        changed: Boolean = false,
        appReentered: Boolean = false,
    ): ForegroundDecision {
        this.reason = reason
        return ForegroundDecision(
            confirmed,
            pending,
            reason,
            changed,
            task,
            lastNodes,
            appReentered,
        )
    }

    @Synchronized
    fun observe(
        source: ForegroundSource,
        value: ForegroundIdentity,
        policy: MatchTimingPolicy,
        now: Long = nowMillis(),
    ): ForegroundDecision {
        if (surfaceCovered == true && value.appId != SYSTEM_UI) {
            if (source == ForegroundSource.TaskStack) task = value
            return decision(ForegroundDecisionReason.TaskBelowSystemSurface)
        }
        if (
            surfaceCovered == false &&
                value.appId == SYSTEM_UI &&
                pending?.appId?.let { it != SYSTEM_UI } == true
        ) {
            return decision(ForegroundDecisionReason.ExitingSystemSurface)
        }
        val sameApp = value.appId == confirmed.appId
        val same = sameApp && (value.activityId == null || value.activityId == confirmed.activityId)
        if (source != ForegroundSource.Nodes) {
            if (source == ForegroundSource.TaskStack) {
                val repeatedTask = task == value
                task = value
                // A task is not necessarily the interactive surface: notification shade,
                // keyguard and launcher feeds can expose nodes from another package.
                if (repeatedTask && !sameApp && lastNodes == confirmed.appId && pending == null) {
                    return decision(ForegroundDecisionReason.TaskBelowConfirmedSurface)
                }
            }
            // A -> B -> A can finish before a single node read returns. Retain the
            // new visit, but still require fresh A nodes before allowing its rules.
            if (
                sameApp &&
                    pending?.appId?.let { it != value.appId } == true &&
                    pendingSource != ForegroundSource.Nodes
            ) {
                pendingAppReentry = true
            }
            if ((!same || pending != null) && pending != value) {
                pending = value
                pendingSince = now
                pendingSource = source
            }
            return decision(
                if (pending == null) ForegroundDecisionReason.Unchanged
                else ForegroundDecisionReason.AwaitingNodes,
            )
        }
        val repeated =
            lastNodes == value.appId &&
                lastNodesAt?.let { now - it >= policy.foregroundRetryMillis } == true
        val previousNodes = lastNodes
        lastNodes = value.appId
        if (lastNodesAt == null || repeated || previousNodes != value.appId) lastNodesAt = now
        val candidate = pending
        // A node associated with a specific window cannot confirm a different pending Activity
        // merely because both windows belong to the same package.
        val knownNodeConflictsWithPendingIdentity =
            value.activityId != null && candidate != null && candidate != value
        if (knownNodeConflictsWithPendingIdentity) {
            return decision(ForegroundDecisionReason.ForegroundConflict)
        }
        if (
            (value.activityId != null && !same) ||
                (candidate?.appId == value.appId &&
                    (pendingSource != ForegroundSource.Nodes || repeated)) ||
                (!same && repeated)
        ) {
            val matchingTask = task?.takeIf { it.appId == value.appId }
            val next =
                if (value.activityId != null) value
                else if (candidate?.appId == value.appId) {
                    if (candidate.activityId == null) matchingTask ?: candidate else candidate
                } else matchingTask ?: value
            val changed = next != confirmed
            val appReentered = pendingAppReentry && next.appId == confirmed.appId
            confirmed = next
            pending = null
            pendingAppReentry = false
            if (next.appId != SYSTEM_UI && surfaceCovered != true) surfaceReturn = null
            return decision(
                if (appReentered) ForegroundDecisionReason.AppReentered
                else if (task != null && task?.appId != next.appId)
                    ForegroundDecisionReason.SurfaceConfirmed
                else ForegroundDecisionReason.NodesConfirmed,
                changed,
                appReentered,
            )
        }
        if (!same && candidate == null) {
            pending = value
            pendingSince = now
            pendingSource = ForegroundSource.Nodes
        }
        // Persistent nodes for the confirmed app disprove a callback that never materialized.
        if (
            same &&
                candidate != null &&
                now - pendingSince >= policy.foregroundWaitMillis &&
                repeated
        ) {
            pending = null
            pendingAppReentry = false
            return decision(ForegroundDecisionReason.StaleObservationDiscarded)
        }
        return decision(
            if (pending == null) ForegroundDecisionReason.Unchanged
            else ForegroundDecisionReason.ForegroundConflict,
        )
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }
}
