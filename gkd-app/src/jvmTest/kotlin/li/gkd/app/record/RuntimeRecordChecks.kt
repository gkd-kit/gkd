package li.gkd.app.record

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import li.gkd.db.Db
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

/** Queries and fault injection use the real isolated application database. */
suspend fun assertRuntimeRecords(database: File) = coroutineScope {
    BundledSQLiteDriver().open(database.absolutePath).use { connection ->
        // Room also writes its invalidation bookkeeping; use SQLite lock waiting on this test connection.
        connection.prepare("PRAGMA busy_timeout = 5000").use { it.step() }
        fun sql(text: String) {
            connection.prepare(text).use { it.step() }
        }

        fun count(table: String): Long =
            connection.prepare("SELECT COUNT(*) FROM $table").use { it.step(); it.getLong(0) }

        fun visit(id: String): Long =
            connection.prepare("SELECT last_visit_time FROM app_last_visit WHERE app_id = ?").use {
                it.bindText(1, id); check(it.step()); it.getLong(0)
            }

        fun clear() {
            listOf(
                "app_last_visit",
                "activity_log",
                "action_log"
            ).forEach { sql("DELETE FROM $it") }
        }

        suspend fun failure(table: String, block: suspend () -> Unit) {
            sql("CREATE TRIGGER test_record_failure BEFORE INSERT ON $table BEGIN SELECT RAISE(FAIL, 'record failure'); END")
            try {
                block()
            } finally {
                sql("DROP TRIGGER test_record_failure")
            }
        }

        fun action(index: Int) =
            ActionRecordInput("app", null, 7, 9, 3, 2, index, null, index.toLong())
        try {
            clear()
            val self = li.gkd.app.app.applicationId
            val launcher = li.gkd.app.app.launcherAppIdFlow.value
            val records = RuntimeRecordRepository
            records.recordVisit(AppVisitInput(self, launcher, 200_000))
            assertEquals(79_999, visit(self))
            assertEquals(140_000, visit(launcher))
            records.recordVisit(AppVisitInput("app", "com.android.systemui", 300_000))
            assertEquals(299_999, visit("app"))
            assertEquals(240_000, visit("com.android.systemui"))
            records.recordVisit(AppVisitInput("app", "app", 400_000))
            assertEquals(400_000, visit("app"))

            clear()
            failure("app_last_visit") {
                assertFails {
                    records.recordVisit(
                        AppVisitInput(
                            "a",
                            "b",
                            10
                        )
                    )
                }
            }
            failure("action_log") { assertFails { records.recordAction(action(1)) } }
            records.flush()
            assertEquals(0, count("app_last_visit"))
            assertEquals(0, count("action_log"))
            records.recordVisit(AppVisitInput("b", "c", 20))
            records.recordAction(action(2))
            assertEquals(2, count("app_last_visit"))
            assertEquals(listOf(2), Db.actionLogDao.query().first().map { it.ruleIndex })

            clear()
            repeat(15) { records.recordActivity("app", null, it.toLong()) }
            assertEquals(0, count("activity_log"))
            failure("activity_log") { assertFails { records.recordActivity("app", null, 15) } }
            assertEquals(0, count("activity_log"))
            records.recordAction(action(3))
            records.flush()
            records.flush()
            assertEquals(16, count("activity_log"))
            records.recordActivity("app", "After", 20)
            records.recordActivity(self, null, 21)
            assertEquals(18, count("activity_log"))
            records.recordActivity("app", null, 22)
            records.flush()
            assertEquals(19, count("activity_log"))

            clear()
            val concurrent = RuntimeRecordRepository
            (0 until 205).map { async(Dispatchers.Default) { concurrent.recordAction(action(it)) } }
                .awaitAll()
            assertEquals(
                (0 until 205).toSet(),
                Db.actionLogDao.query().first().map { it.ruleIndex }.toSet()
            )
            assertEquals(205, count("action_log"))
            // The 600th process-wide committed action triggers retention; failed writes must not advance the count.
            for (i in 205 until 597) concurrent.recordAction(action(i))
            failure("action_log") { assertFails { concurrent.recordAction(action(597)) } }
            assertEquals(597, count("action_log"))
            concurrent.recordAction(action(600))
            assertEquals(500, count("action_log"))
            assertFalse(Db.actionLogDao.query().first().any { it.ruleIndex == 597 })
        } finally {
            clear()
        }
    }
}
