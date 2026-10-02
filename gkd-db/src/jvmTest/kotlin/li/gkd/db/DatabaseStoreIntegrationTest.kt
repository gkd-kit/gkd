package li.gkd.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** All store checks share the application's single database lifecycle. */
class DatabaseStoreIntegrationTest {
    @Test
    fun storesPreserveAtomicWritesSnapshotsAndRetention() = runBlocking {
        Db.initialize(DatabaseStoreTestSession.path)
        try {
            Db.actionLogDao.count().first()
            SubscriptionConfigStoreChecks().apply {
                explicitAppAndGroupSettingsSurviveChangesToTheirDefaults()
                resettingAppGateAndGroupSettingsPreservesOtherScopesAndPageExclusions()
                aFailedMixedScopeTransactionDoesNotLeavePartiallyChangedSwitches()
                importingTwiceKeepsLocalBusinessKeysAndAddsOnlyMissingOverrides()
                subscriptionUpsertPreservesOverridesAndDeletionCascadesToAllFourTables()
                checkpointRestoresDeletedAndChangedOverridesAndRemovesImportedRows()
                observedSnapshotsContainCompleteTransactionsAcrossBothGroupTables()
                failedImportTransactionRollsBackAllConfigurationTables()
                concurrentGlobalGroupUpdatesPreserveEveryChangeToTheSameColumn()
                changingAnAppGroupSwitchKeepsTheLatestExclusionAndFailedEditsLeaveItUntouched()
                failedImportDoesNotRollBackANormalWriteWaitingForTheTransaction()
            }
            RuntimeRecordStoreChecks().apply {
                secondVisitFailureRollsBackFirstVisitAndRetryKeepsNewestValue()
                actionTrimFailureRollsBackInsertAndRetryDoesNotDuplicate()
                activityTrimFailureRetainsOldRowsAndRollsBackTheWholeBatch()
                timestampRetentionPreservesExistingTieBoundaryWhileActionsUseIds()
            }
        } finally {
            Db.close()
        }
        Unit
    }
}

object DatabaseStoreTestSession {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { it.resolve("settings.gradle.kts").isFile }
    val path = root.resolve(".local/tests/desktop/database-${UUID.randomUUID()}/gkd.db")
        .apply { parentFile.mkdirs() }.absolutePath

    fun execute(sql: String) = BundledSQLiteDriver().open(path).use { it.execSQL(sql) }

    fun clear() {
        // Reset persisted test data, not the application's singleton instances.
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("DROP TRIGGER IF EXISTS fail_visit")
            connection.execSQL("DROP TRIGGER IF EXISTS fail_trim")
            listOf("subs_app_config", "subs_category_config", "subs_app_group_config",
                "subs_global_group_config", "subs_item", "app_last_visit", "action_log", "activity_log")
                .forEach { connection.execSQL("DELETE FROM $it") }
        }
    }
}
