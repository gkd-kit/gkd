package li.gkd.app.feature.snapshot

import li.gkd.app.util.SnapshotDisplayModeOption
import li.gkd.db.Snapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SnapshotGroup(
    val key: String,
    val title: String,
    val snapshots: List<Snapshot>,
    val appId: String? = null,
)

fun buildSnapshotGroups(
    snapshots: List<Snapshot>,
    appNames: Map<String, String>,
    displayMode: SnapshotDisplayModeOption,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<SnapshotGroup> {
    val sorted = snapshots.sortedByDescending { it.id }
    return when (displayMode) {
        SnapshotDisplayModeOption.ByTime -> sorted
            .groupBy { Instant.ofEpochMilli(it.id).atZone(zoneId).toLocalDate() }
            .map { (date, items) ->
                val title = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                SnapshotGroup(key = "date:$title", title = title, snapshots = items)
            }

        SnapshotDisplayModeOption.ByApp -> sorted
            .groupBy { it.appId }
            .map { (appId, items) ->
                SnapshotGroup(
                    key = "app:$appId",
                    title = appNames[appId] ?: appId,
                    snapshots = items,
                    appId = appId,
                )
            }
    }
}
