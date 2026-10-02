package li.gkd.app.backup

import kotlinx.serialization.json.Json
import li.gkd.app.rule.RuleGroupPolicy
import li.gkd.app.rule.RuleGroupSummaryBuilder
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val policy = RuleGroupPolicy()

    // Frozen V1 archive payload: identity, discriminator and omitted nulls are a compatibility contract.
    private val legacy = """
        {
          "subsItems": [{"id":7,"ctime":1,"mtime":2,"enable":true,"enableUpdate":true,"order":0}],
          "appConfigs": [
            {"id":20,"subsId":7,"appId":"app.one","enable":false},
            {"id":10,"subsId":7,"appId":"app.one","enable":true}
          ],
          "categoryConfigs": [
            {"id":80,"subsId":7,"categoryKey":3,"enable":false},
            {"id":70,"subsId":7,"categoryKey":3}
          ],
          "subsConfigs": [
            {"id":40,"type":2,"subsId":7,"appId":"app.one","groupKey":4,"enable":false,"exclude":"new"},
            {"id":30,"type":2,"subsId":7,"appId":"app.one","groupKey":4,"exclude":"old"},
            {"id":60,"type":3,"subsId":7,"groupKey":4,"enable":true,"exclude":"new-global"},
            {"id":50,"type":3,"subsId":7,"groupKey":4,"enable":false,"exclude":"old-global"}
          ]
        }
    """.trimIndent()

    @Test
    fun legacyBackupSplitsAndDeduplicatesWholeRowsUsingTheSmallestId() {
        val data = BackupFormat.decode(legacy).toSnapshot()
        assertEquals(listOf(SubsAppConfig(true, 7, "app.one")), data.appConfigs)
        assertEquals(listOf(SubsCategoryConfig(null, 7, 3)), data.categoryConfigs)
        assertEquals(listOf(SubsAppGroupConfig(7, "app.one", 4, null, "old")), data.appGroupConfigs)
        assertEquals(
            listOf(SubsGlobalGroupConfig(7, 4, false, "old-global")),
            data.globalGroupConfigs
        )
    }

    @Test
    fun importedConfigurationGivesUiAndRuntimeTheSameEffectiveSwitchState() {
        val data = BackupFormat.decode(legacy).toSnapshot()
        val subscription = RawSubscription.parse(
            """
            {
              id: 7, name: 'Sample', version: 1,
              categories: [{key: 3, name: 'Batch', enable: false}],
              apps: [{id: 'app.one', groups: [{key: 4, name: 'Batch', enable: true, rules: []}]}],
              globalGroups: [{key: 4, name: 'Global', enable: true, rules: []}],
            }
            """.trimIndent(),
        )
        val group = subscription.apps.single().groups.single()
        val category = subscription.categories.single()
        val uiEnabled = policy.getGroupEnabled(
            group,
            data.appGroupConfigs.find { it.groupKey == group.key },
            category,
            data.categoryConfigs.find { it.categoryKey == category.key },
        )
        val summary = RuleGroupSummaryBuilder.build(
            policy,
            subscriptions = listOf(UsedSubsEntry(data.subsItems.single(), subscription)),
            appInfoById = emptyMap(),
            appConfigs = data.appConfigs,
            groupConfigs = data.appGroupConfigs + data.globalGroupConfigs,
            categoryConfigs = data.categoryConfigs,
        )
        assertTrue(uiEnabled)
        assertEquals(uiEnabled, summary.appIdToAllGroups.getValue("app.one").single().enable)
        assertTrue(summary.globalGroups.isEmpty())
    }

    @Test
    fun reexportedBackupKeepsNullRecordsAndAllConfigurationValues() {
        val converted = BackupFormat.decode(legacy)
        val encoded = BackupFormat.encode(converted)
        assertTrue(encoded.contains("\"formatVersion\":2"))
        assertFalse(encoded.contains("\"subsConfigs\""))
        assertEquals(converted.toSnapshot(), BackupFormat.decode(encoded).toSnapshot())
        assertNull(BackupFormat.decode(encoded).categoryConfigs.single().enable)
    }

    @Test
    fun version2BackupBeforeTableRenamingKeepsItsFieldNamesAndValues() {
        // Frozen database-v15 export: Room names must not change the V2 archive contract.
        val exportedBeforeRename = """
            {
              "formatVersion":2,
              "subsItems":[{"id":7,"ctime":1,"mtime":2,"enable":true,"enableUpdate":true,"order":0}],
              "appConfigs":[{"subsId":7,"appId":"app.one","enable":false}],
              "categoryConfigs":[{"subsId":7,"categoryKey":3}],
              "appGroupConfigs":[{"subsId":7,"appId":"app.one","groupKey":4,"exclude":"app-exclude"}],
              "globalGroupConfigs":[{"subsId":7,"groupKey":4,"enable":true,"exclude":"global-exclude"}]
            }
        """.trimIndent()
        val expected = SubscriptionConfigSnapshot(
            subsItems = listOf(SubsItem(7, ctime = 1, mtime = 2, enable = true, order = 0)),
            appConfigs = listOf(SubsAppConfig(false, 7, "app.one")),
            categoryConfigs = listOf(SubsCategoryConfig(null, 7, 3)),
            appGroupConfigs = listOf(SubsAppGroupConfig(7, "app.one", 4, null, "app-exclude")),
            globalGroupConfigs = listOf(SubsGlobalGroupConfig(7, 4, true, "global-exclude")),
        )
        assertEquals(expected, BackupFormat.decode(exportedBeforeRename).toSnapshot())
        val reexported = BackupFormat.encode(BackupDatabaseData.fromSnapshot(expected))
        assertEquals(
            Json.parseToJsonElement(exportedBeforeRename),
            Json.parseToJsonElement(reexported)
        )
    }

    @Test
    fun legacyMissingCollectionsAndOptionalGroupFieldsUseOriginalDefaults() {
        val data = BackupFormat.decode(
            """{"subsConfigs":[{"id":1,"type":3,"subsId":7,"groupKey":4}]}""",
        ).toSnapshot()
        assertTrue(data.categoryConfigs.isEmpty())
        assertEquals(listOf(SubsGlobalGroupConfig(7, 4)), data.globalGroupConfigs)
    }

    @Test
    fun unsupportedVersionsAndTypesFailBeforeImportCanStart() {
        assertThrows(li.gkd.app.storage.StorageException::class.java) {
            BackupFormat.decode("""{"formatVersion":99}""")
        }
        assertThrows(li.gkd.app.storage.StorageException::class.java) {
            BackupFormat.decode("""{"subsConfigs":[{"id":1,"type":99,"subsId":7}]}""")
        }
    }
}
