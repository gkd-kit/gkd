package li.gkd.app.feature.snapshot

import li.gkd.app.util.SnapshotDisplayModeOption
import li.gkd.db.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SnapshotGroupingTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun timeGroupsKeepNewestDateAndSnapshotFirst() {
        val old = snapshot("2026-09-23T08:00:00Z", "app.a")
        val newer = snapshot("2026-09-24T09:00:00Z", "app.b")
        val newest = snapshot("2026-09-24T10:00:00Z", "app.a")

        val groups = buildSnapshotGroups(
            snapshots = listOf(old, newer, newest),
            appNames = emptyMap(),
            displayMode = SnapshotDisplayModeOption.ByTime,
            zoneId = zone,
        )

        assertEquals(listOf("2026-09-24", "2026-09-23"), groups.map { it.title })
        assertEquals(listOf(newest.id, newer.id), groups.first().snapshots.map { it.id })
    }

    @Test
    fun timeGroupsUseDeviceTimezoneForDateBoundary() {
        val beforeMidnight = snapshot("2026-09-24T15:59:00Z", "app.a")
        val afterMidnight = snapshot("2026-09-24T16:01:00Z", "app.a")

        val groups = buildSnapshotGroups(
            snapshots = listOf(beforeMidnight, afterMidnight),
            appNames = emptyMap(),
            displayMode = SnapshotDisplayModeOption.ByTime,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(listOf("2026-09-25", "2026-09-24"), groups.map { it.title })
    }

    @Test
    fun appGroupsUseLatestSnapshotOrderAndIncludeUploadedItems() {
        val oldPending = snapshot("2026-09-23T08:00:00Z", "app.a")
        val uploaded = snapshot("2026-09-24T09:00:00Z", "app.b", assetId = 1)
        val newestPending = snapshot("2026-09-24T10:00:00Z", "app.c")
        val earlierSameApp = snapshot("2026-09-22T10:00:00Z", "app.c")

        val groups = buildSnapshotGroups(
            snapshots = listOf(oldPending, uploaded, newestPending, earlierSameApp),
            appNames = mapOf("app.a" to "应用 A"),
            displayMode = SnapshotDisplayModeOption.ByApp,
            zoneId = zone,
        )

        assertEquals(listOf("app:app.c", "app:app.b", "app:app.a"), groups.map { it.key })
        assertEquals(listOf("app.c", "app.b", "应用 A"), groups.map { it.title })
        assertEquals(listOf(newestPending.id, earlierSameApp.id), groups.first().snapshots.map { it.id })
        assertEquals(listOf(newestPending.id, earlierSameApp.id, uploaded.id, oldPending.id), groups.flatMap { group ->
            group.snapshots.map { it.id }
        })
    }

    private fun snapshot(time: String, appId: String, assetId: Int? = null) = Snapshot(
        id = Instant.parse(time).toEpochMilli(),
        appId = appId,
        activityId = null,
        screenHeight = 1,
        screenWidth = 1,
        isLandscape = false,
        githubAssetId = assetId,
    )
}
