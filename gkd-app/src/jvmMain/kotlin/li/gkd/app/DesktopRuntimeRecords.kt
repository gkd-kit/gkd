package li.gkd.app

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import li.gkd.app.record.ActionRecordInput
import li.gkd.app.record.AppVisitInput
import li.gkd.db.ActionLog
import li.gkd.db.Db

/** Explicit synthetic event input, never an Accessibility action or fixture import. */
@Serializable
data class DesktopRuntimeRecordRequest(
    val activities: List<DesktopActivityRecord> = emptyList(),
    val visits: List<AppVisitInput> = emptyList(),
    val actions: List<ActionRecordInput> = emptyList(),
)

@Serializable
data class DesktopActivityRecord(val appId: String, val activityId: String? = null, val time: Long)

@Serializable
data class DesktopRuntimeRecordSnapshot(
    val activityCount: Int,
    val actionCount: Int,
    val visits: List<String>,
    val latest: ActionLog?,
)

suspend fun DesktopRuntime.replayRecords(request: DesktopRuntimeRecordRequest): DesktopRuntimeRecordSnapshot {
    require(DesktopStorage.isolated) { "Runtime record replay requires --test" }
    require(request.activities.size + request.visits.size + request.actions.size <= 2000)
    request.activities.forEach {
        li.gkd.app.record.RuntimeRecordRepository.recordActivity(
            it.appId,
            it.activityId,
            it.time
        )
    }
    request.visits.forEach { li.gkd.app.record.RuntimeRecordRepository.recordVisit(it) }
    request.actions.forEach { li.gkd.app.record.RuntimeRecordRepository.recordAction(it) }
    li.gkd.app.record.RuntimeRecordRepository.flush()
    return DesktopRuntimeRecordSnapshot(
        Db.activityLogDao.count().first(), Db.actionLogDao.count().first(),
        Db.appLastVisitDao.query().first(), Db.actionLogDao.queryLatest().first(),
    )
}
