package li.songe.gkd.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import li.songe.gkd.data.ActivityLog
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.ClickLog
import li.songe.gkd.data.Snapshot
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem

@Database(
    version = 8,
    entities = [
        SubsItem::class,
        Snapshot::class,
        SubsConfig::class,
        ClickLog::class,
        CategoryConfig::class,
        ActivityLog::class
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = ActivityLog.ActivityLogV2Spec::class),
    ]
)
abstract class AppDb : RoomDatabase() {
    abstract fun subsItemDao(): SubsItem.SubsItemDao
    abstract fun snapshotDao(): Snapshot.SnapshotDao
    abstract fun subsConfigDao(): SubsConfig.SubsConfigDao
    abstract fun clickLogDao(): ClickLog.TriggerLogDao
    abstract fun categoryConfigDao(): CategoryConfig.CategoryConfigDao
    abstract fun activityLogDao(): ActivityLog.ActivityLogDao
}