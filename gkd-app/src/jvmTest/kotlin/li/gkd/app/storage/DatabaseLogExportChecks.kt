package li.gkd.app.storage

import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.*
import li.gkd.app.util.JsonUtils
import li.gkd.db.*

/** Protects the diagnostic ZIP contract: complete readable tables, never live SQLite files. */
fun assertDatabaseLogJsonExport() = runBlocking {
    val subsId = 9_001_234L
    val appId = "test.export"
    Db.withTransaction {
        Db.subsItemDao.upsert(SubsItem(id = subsId, order = 0))
        Db.subsAppConfigDao.upsert(SubsAppConfig(false, subsId, appId))
        Db.subsCategoryConfigDao.upsert(SubsCategoryConfig(subsId = subsId, categoryKey = 1))
        Db.subsAppGroupConfigDao.upsert(SubsAppGroupConfig(subsId, appId, 2))
        Db.subsGlobalGroupConfigDao.upsert(SubsGlobalGroupConfig(subsId, 3))
        Db.snapshotDao.insert(Snapshot(subsId, appId, null, 800, 400, false))
        Db.actionLogDao.insert(
            *List(1_005) { index ->
                    ActionLog(
                        ctime = index.toLong(),
                        appId = appId,
                        subsId = subsId,
                        subsVersion = 0,
                        groupKey = 2,
                        groupType = RuleGroupType.App,
                        ruleIndex = 0,
                    )
                }
                .toTypedArray(),
        )
        Db.activityLogDao.insert(ActivityLog(ctime = subsId, appId = appId))
        Db.appLastVisitDao.insert(AppLastVisit(appId, subsId))
        Db.ruleTriggerStatsDao.insertIfAbsent(
            RuleTriggerStats(subsId, RuleGroupType.App, 2, appId, 1005, subsId),
        )
        Db.a11yEventLogDao.insert(
            listOf(
                A11yEventLog(
                    id = 9_001_234,
                    ctime = subsId,
                    type = 1,
                    appId = appId,
                    name = "中文 \"引号\" \\ 换行\nemoji 😀",
                    desc = null,
                    text = "[\"内容\"]",
                ),
            ),
        )
        Db.matchDiagnosticRecordDao.upsert(
            MatchDiagnosticRecord(subsId, subsId, subsId + 10, 10, 0, listOf(appId)),
        )
    }
    val expected = captureDatabaseLogSnapshot()
    assertEquals(
        SubsAppConfig(false, subsId, appId),
        expected.appConfigs.single { it.subsId == subsId },
    )
    assertEquals(
        SubsCategoryConfig(subsId = subsId, categoryKey = 1),
        expected.categoryConfigs.single { it.subsId == subsId },
    )
    assertEquals(
        SubsAppGroupConfig(subsId, appId, 2),
        expected.appGroupConfigs.single { it.subsId == subsId },
    )
    assertEquals(
        SubsGlobalGroupConfig(subsId, 3),
        expected.globalGroupConfigs.single { it.subsId == subsId },
    )
    assertEquals(
        Snapshot(subsId, appId, null, 800, 400, false),
        expected.snapshots.single { it.id == subsId },
    )
    assertEquals(AppLastVisit(appId, subsId), expected.appLastVisits.single { it.appId == appId })
    assertEquals(
        RuleTriggerStats(subsId, RuleGroupType.App, 2, appId, 1005, subsId),
        expected.ruleTriggerStats.single { it.subsId == subsId },
    )
    assertEquals(subsId, expected.activityLogs.single { it.appId == appId }.ctime)
    assertEquals(
        MatchDiagnosticRecord(subsId, subsId, subsId + 10, 10, 0, listOf(appId)),
        expected.matchDiagnostics.single { it.id == subsId },
    )
    val startedAt = System.currentTimeMillis()
    // Use the real archive builder; the fixture diagnostic row has no attachment directory.
    val archive = LogArchive.build(emptyMap())
    ZipFile(archive).use { zip ->
        val databaseNames =
            zip.entries().asSequence().map { it.name }.filter { it.startsWith("db/") }.toSet()
        val expectedTables =
            mapOf(
                "subs_item" to JsonUtils.keepingNulls.encodeToJsonElement(expected.subsItems),
                "subs_app_config" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.appConfigs),
                "subs_category_config" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.categoryConfigs),
                "subs_app_group_config" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.appGroupConfigs),
                "subs_global_group_config" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.globalGroupConfigs),
                "snapshot" to JsonUtils.keepingNulls.encodeToJsonElement(expected.snapshots),
                "action_log" to JsonUtils.keepingNulls.encodeToJsonElement(expected.actionLogs),
                "rule_trigger_stats" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.ruleTriggerStats),
                "activity_log" to JsonUtils.keepingNulls.encodeToJsonElement(expected.activityLogs),
                "app_last_visit" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.appLastVisits),
                "a11y_event_log" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.a11yEventLogs),
                "match_diagnostic" to
                    JsonUtils.keepingNulls.encodeToJsonElement(expected.matchDiagnostics),
            )
        assertEquals(
            expectedTables.keys.map { "db/$it.json" }.toSet() + "db/manifest.json",
            databaseNames,
        )
        assertFalse(
            zip.entries().asSequence().any {
                it.name.endsWith(".db") || it.name.endsWith("-wal") || it.name.endsWith("-shm")
            },
        )
        val manifest =
            Json.parseToJsonElement(
                    zip.getInputStream(zip.getEntry("db/manifest.json")).bufferedReader().use {
                        it.readText()
                    },
                )
                .jsonObject
        assertEquals(1, manifest.getValue("formatVersion").jsonPrimitive.int)
        assertEquals(
            expected.databaseVersion,
            manifest.getValue("databaseVersion").jsonPrimitive.int,
        )
        assertTrue(
            manifest.getValue("exportedAt").jsonPrimitive.long in
                startedAt..System.currentTimeMillis(),
        )
        for ((table, rows) in expectedTables) {
            val text =
                zip.getInputStream(zip.getEntry("db/$table.json")).bufferedReader().use {
                    it.readText()
                }
            assertEquals(rows, Json.parseToJsonElement(text), table)
            assertEquals(
                rows.jsonArray.size,
                manifest.getValue("tables").jsonObject.getValue(table).jsonPrimitive.int,
            )
            assertTrue(text.contains('\n'), "Nonempty tables are formatted for reading: $table")
        }
        val actions = expectedTables.getValue("action_log").jsonArray
        assertEquals(
            1005,
            actions.count { it.jsonObject.getValue("subsId").jsonPrimitive.long == subsId },
        )
        val event =
            expectedTables
                .getValue("a11y_event_log")
                .jsonArray
                .single { it.jsonObject.getValue("id").jsonPrimitive.int == 9_001_234 }
                .jsonObject
        assertEquals(JsonNull, event.getValue("desc"))
        assertEquals("中文 \"引号\" \\ 换行\nemoji 😀", event.getValue("name").jsonPrimitive.content)
        val item =
            expectedTables
                .getValue("subs_item")
                .jsonArray
                .single { it.jsonObject.getValue("id").jsonPrimitive.long == subsId }
                .jsonObject
        assertEquals(JsonPrimitive(false), item.getValue("enable"))
        assertEquals(JsonNull, item.getValue("updateUrl"))
    }
    // With the table emptied through its production DAO, the ZIP must still contain [].
    Db.matchDiagnosticRecordDao.queryAll().forEach { Db.matchDiagnosticRecordDao.delete(it.id) }
    ZipFile(LogArchive.build(emptyMap())).use { zip ->
        assertEquals(
            "[]",
            zip.getInputStream(zip.getEntry("db/match_diagnostic.json")).bufferedReader().use {
                it.readText()
            },
        )
    }
    // A concurrent transaction changes two tables together; exports must never mix generations.
    withTimeout(10_000) {
        coroutineScope {
            val writer =
                launch(Dispatchers.Default) {
                    repeat(30) { generation ->
                        Db.withTransaction {
                            Db.subsAppGroupConfigDao.upsert(
                                SubsAppGroupConfig(subsId, appId, 2, exclude = "$generation"),
                            )
                            yield()
                            Db.subsGlobalGroupConfigDao.upsert(
                                SubsGlobalGroupConfig(subsId, 3, exclude = "$generation"),
                            )
                        }
                        yield()
                    }
                }
            repeat(30) {
                val snapshot = captureDatabaseLogSnapshot()
                assertEquals(
                    snapshot.appGroupConfigs.single { it.subsId == subsId }.exclude,
                    snapshot.globalGroupConfigs.single { it.subsId == subsId }.exclude,
                )
                yield()
            }
            writer.join()
        }
    }
}
