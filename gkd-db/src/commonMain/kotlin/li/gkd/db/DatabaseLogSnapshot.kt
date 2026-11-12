package li.gkd.db

import androidx.room3.withReadTransaction

data class DatabaseLogSnapshot(
    val exportedAt: Long,
    val subsItems: List<SubsItem>,
    val appConfigs: List<SubsAppConfig>,
    val categoryConfigs: List<SubsCategoryConfig>,
    val appGroupConfigs: List<SubsAppGroupConfig>,
    val globalGroupConfigs: List<SubsGlobalGroupConfig>,
    val snapshots: List<Snapshot>,
    val actionLogs: List<ActionLog>,
    val ruleTriggerStats: List<RuleTriggerStats>,
    val activityLogs: List<ActivityLog>,
    val appLastVisits: List<AppLastVisit>,
    val a11yEventLogs: List<A11yEventLog>,
    val matchDiagnostics: List<MatchDiagnosticRecord>,
) {
    val databaseVersion: Int = AppDbVersion
}

/** All business tables from one database read transaction, without UI query limits. */
suspend fun captureDatabaseLogSnapshot(): DatabaseLogSnapshot =
    Db.database.withReadTransaction {
        DatabaseLogSnapshot(
            exportedAt = System.currentTimeMillis(),
            subsItems = Db.database.subsItemDao().queryAll().sortedBy { it.id },
            appConfigs =
                Db.database
                    .subsAppConfigDao()
                    .queryAll()
                    .sortedWith(compareBy({ it.subsId }, { it.appId })),
            categoryConfigs =
                Db.database
                    .subsCategoryConfigDao()
                    .queryAll()
                    .sortedWith(compareBy({ it.subsId }, { it.categoryKey })),
            appGroupConfigs =
                Db.database
                    .subsAppGroupConfigDao()
                    .queryAll()
                    .sortedWith(compareBy({ it.subsId }, { it.appId }, { it.groupKey })),
            globalGroupConfigs =
                Db.database
                    .subsGlobalGroupConfigDao()
                    .queryAll()
                    .sortedWith(compareBy({ it.subsId }, { it.groupKey })),
            snapshots = Db.database.snapshotDao().queryAll(),
            actionLogs = Db.database.actionLogDao().queryAll(),
            ruleTriggerStats = Db.database.ruleTriggerStatsDao().queryAll(),
            activityLogs = Db.database.activityLogDao().queryAll(),
            appLastVisits = Db.database.appLastVisitDao().queryAll(),
            a11yEventLogs = Db.database.a11yEventLogDao().queryAll(),
            matchDiagnostics = Db.database.matchDiagnosticRecordDao().queryAll(),
        )
    }
