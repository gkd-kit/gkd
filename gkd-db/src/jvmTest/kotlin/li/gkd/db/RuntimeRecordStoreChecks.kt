package li.gkd.db

import androidx.paging.PagingSource
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RuntimeRecordStoreChecks {
    private fun database(block: suspend (AppDb, RuntimeRecordStore, (String) -> Unit) -> Unit) = runBlocking {
        DatabaseStoreTestSession.clear()
        block(Db.database, RuntimeRecordStore, DatabaseStoreTestSession::execute)
    }

    private fun action(time: Long) = ActionLog(
        ctime = time, appId = "app", subsId = 7, subsVersion = 1,
        groupKey = 2, groupType = RuleGroupType.App, ruleIndex = 0,
    )

    fun appFiltersKeepSubscriptionScopeAndIncludeGlobalRuleActions() = database { db, _, _ ->
        val dao = db.actionLogDao()
        // The same app occurs in two subscriptions, and global-rule actions belong to the
        // foreground app too. Filtering must not leak another subscription or drop those actions.
        dao.insert(
            action(1).copy(appId = "known"),
            action(2).copy(appId = "other"),
            action(3).copy(appId = "known", subsId = 8),
            action(4).copy(appId = "known", groupType = RuleGroupType.Global),
            action(5).copy(appId = "uninstalled", subsId = 8),
        )
        assertEquals(listOf("uninstalled", "known", "other"), dao.queryAppIds(null).first())
        assertEquals(listOf("known", "other"), dao.queryAppIds(7).first())
        assertEquals(emptyList(), dao.queryAppIds(9).first())

        suspend fun times(subsId: Long? = null, appIds: List<String> = emptyList()): List<Long> {
            val source = dao.pagingSource(subsId, appIds)
            try {
                val page = assertIs<PagingSource.LoadResult.Page<Int, ActionLog>>(
                    source.load(PagingSource.LoadParams.Refresh(null, 100, false))
                )
                return page.data.map { it.ctime }
            } finally {
                source.invalidate()
            }
        }
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), times())
        assertEquals(listOf(4L, 2L, 1L), times(subsId = 7))
        assertEquals(listOf(4L, 3L, 1L), times(appIds = listOf("known")))
        assertEquals(listOf(4L, 1L), times(subsId = 7, appIds = listOf("known")))
        assertEquals(emptyList(), times(subsId = 7, appIds = listOf("uninstalled")))
        // Multiple apps form a union; clearing the selection restores the whole current scope.
        assertEquals(listOf(4L, 3L, 2L, 1L), times(appIds = listOf("known", "other")))
        assertEquals(listOf(4L, 2L, 1L), times(subsId = 7, appIds = listOf("known", "other")))
        assertEquals(listOf(4L, 1L), times(subsId = 7, appIds = listOf("known", "uninstalled")))
    }

    fun secondVisitFailureRollsBackFirstVisitAndRetryKeepsNewestValue() = database { db, store, sql ->
        store.writeVisits(listOf(AppLastVisit("old", 1)), false)
        sql("CREATE TRIGGER fail_visit BEFORE INSERT ON app_last_visit WHEN NEW.app_id = 'new' BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { store.writeVisits(listOf(AppLastVisit("old", 10), AppLastVisit("new", 11)), false) }
        store.writeVisits(listOf(AppLastVisit("middle", 5)), false)
        assertEquals(listOf("middle", "old"), db.appLastVisitDao().query().first())
        sql("DROP TRIGGER fail_visit")
        store.writeVisits(listOf(AppLastVisit("old", 10), AppLastVisit("new", 11)), false)
        assertEquals(listOf("new", "old", "middle"), db.appLastVisitDao().query().first())
        store.writeVisits(listOf(AppLastVisit("new", 20), AppLastVisit("new", 21)), false)
        assertEquals(listOf("new", "old", "middle"), db.appLastVisitDao().query().first())
    }

    fun actionTrimFailureRollsBackInsertAndRetryDoesNotDuplicate() = database { db, store, sql ->
        db.actionLogDao().insert(*(0L until 501).map(::action).toTypedArray())
        sql("CREATE TRIGGER fail_trim BEFORE DELETE ON action_log BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { store.writeAction(action(999), true) }
        assertEquals(501, db.actionLogDao().count().first())
        assertEquals(500, db.actionLogDao().queryLatest().first()!!.ctime)
        sql("DROP TRIGGER fail_trim")
        store.writeAction(action(999), true)
        assertEquals(500, db.actionLogDao().count().first())
        assertEquals(1, db.actionLogDao().query().first().count { it.ctime == 999L })
    }

    fun activityTrimFailureRetainsOldRowsAndRollsBackTheWholeBatch() = database { db, store, sql ->
        db.activityLogDao().insert(*(0L until 501).map { ActivityLog(ctime = it, appId = "app") }.toTypedArray())
        val batch = listOf(ActivityLog(ctime = 900, appId = "a"), ActivityLog(ctime = 901, appId = "b"))
        sql("CREATE TRIGGER fail_trim BEFORE DELETE ON activity_log BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { store.writeActivities(batch, true) }
        assertEquals(501, db.activityLogDao().count().first())
        sql("DROP TRIGGER fail_trim")
        store.writeActivities(batch, true)
        assertEquals(500, db.activityLogDao().count().first())
    }

    fun timestampRetentionPreservesExistingTieBoundaryWhileActionsUseIds() = database { db, store, _ ->
        // Persisted log retention uses <= the 501st timestamp, so ties at that boundary are removed.
        store.writeActivities(List(501) { ActivityLog(ctime = 10, appId = "app") }, true)
        assertEquals(0, db.activityLogDao().count().first())
        store.writeVisits(List(501) { AppLastVisit("app.$it", 10) }, true)
        assertEquals(emptyList(), db.appLastVisitDao().query().first())
        db.actionLogDao().insert(*List(501) { action(10) }.toTypedArray())
        store.writeAction(action(10), true)
        assertEquals(500, db.actionLogDao().count().first())
    }
}
