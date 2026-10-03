package li.gkd.app.record

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.app.applicationId
import li.gkd.app.app.launcherAppIdFlow
import li.gkd.app.util.Constants
import li.gkd.db.ActionLog
import li.gkd.db.ActivityLog
import li.gkd.db.AppLastVisit
import li.gkd.db.RuntimeRecordStore

/** An application transition captured at the event boundary. */
@Serializable
data class AppVisitInput(
    val oldAppId: String,
    val newAppId: String,
    val time: Long,
)

@Serializable
data class ActionRecordInput(
    val appId: String,
    val activityId: String?,
    val subsId: Long,
    val subsVersion: Int,
    val groupKey: Int,
    val groupType: Int,
    val ruleIndex: Int,
    val ruleKey: Int?,
    val time: Long,
)

/** Application-owned record writer. The host supplies the calling coroutine and handles failures.
 * Only partial/failed Activity batches are buffered; visits and actions are never queued for retry.
 */
object RuntimeRecordRepository {
    private const val TRIM_INTERVAL = 100L

    private val writer = Mutex()
    private val activities = mutableListOf<ActivityLog>()
    private var visitCount = 0L
    private var activityCount = 0L
    private var actionCount = 0L

    suspend fun recordVisit(input: AppVisitInput) {
        val currentLauncherAppId = launcherAppIdFlow.value
        fun weighted(appId: String, time: Long) = when (appId) {
            applicationId -> time - 120_000
            currentLauncherAppId, Constants.systemUiAppId -> time - 60_000
            else -> time
        }

        val records = listOf(
            AppLastVisit(input.oldAppId, weighted(input.oldAppId, input.time - 1)),
            AppLastVisit(input.newAppId, weighted(input.newAppId, input.time)),
        )
        writer.withLock {
            withContext(NonCancellable) {
                RuntimeRecordStore.writeVisits(records, visitCount % TRIM_INTERVAL == 0L)
                visitCount++
            }
        }
    }

    suspend fun recordActivity(appId: String, activityId: String?, time: Long) = writer.withLock {
        activities.add(ActivityLog(ctime = time, appId = appId, activityId = activityId))
        if (activities.size >= 16 || appId == applicationId) flushActivities()
    }

    suspend fun recordAction(input: ActionRecordInput) {
        val record = ActionLog(
            appId = input.appId, activityId = input.activityId,
            subsId = input.subsId, subsVersion = input.subsVersion,
            groupKey = input.groupKey, groupType = input.groupType,
            ruleIndex = input.ruleIndex, ruleKey = input.ruleKey, ctime = input.time,
        )
        writer.withLock {
            withContext(NonCancellable) {
                RuntimeRecordStore.writeAction(record, (actionCount + 1) % TRIM_INTERVAL == 0L)
                actionCount++
            }
        }
    }

    /** Submit the remaining Activity batch. Stop producers before the final flush. */
    suspend fun flush() = writer.withLock { flushActivities() }

    private suspend fun flushActivities() {
        if (activities.isEmpty()) return
        // Finish COMMIT and in-memory acknowledgement together, even if the caller is canceled.
        withContext(NonCancellable) {
            val end = activityCount + activities.size
            val trim = activityCount == 0L ||
                    (activityCount - 1) / TRIM_INTERVAL != (end - 1) / TRIM_INTERVAL
            RuntimeRecordStore.writeActivities(activities.toList(), trim)
            activities.clear()
            activityCount = end
        }
    }
}
