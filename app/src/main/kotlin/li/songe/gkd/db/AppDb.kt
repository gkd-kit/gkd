package li.songe.gkd.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import li.songe.gkd.app
import li.songe.gkd.data.A11yEventLog
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.ActivityLog
import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.AppVisitLog
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.Snapshot
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.util.dbFolder
import li.songe.gkd.util.json

@Database(
    version = 14,
    entities = [
        SubsItem::class,
        Snapshot::class,
        SubsConfig::class,
        CategoryConfig::class,
        ActionLog::class,
        ActivityLog::class,
        AppConfig::class,
        AppVisitLog::class,
        A11yEventLog::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = ActivityLog.ActivityLogV2Spec::class),
        AutoMigration(from = 8, to = 9, spec = ActionLog.ActionLogSpec::class),
        AutoMigration(from = 9, to = 10, spec = Migration9To10Spec::class),
        AutoMigration(from = 10, to = 11, spec = Migration10To11Spec::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
    ]
)
@TypeConverters(DbConverters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun subsItemDao(): SubsItem.SubsItemDao
    abstract fun snapshotDao(): Snapshot.SnapshotDao
    abstract fun subsConfigDao(): SubsConfig.SubsConfigDao
    abstract fun appConfigDao(): AppConfig.AppConfigDao
    abstract fun categoryConfigDao(): CategoryConfig.CategoryConfigDao
    abstract fun actionLogDao(): ActionLog.ActionLogDao
    abstract fun activityLogDao(): ActivityLog.ActivityLogDao
    abstract fun appVisitLogDao(): AppVisitLog.AppLogDao
    abstract fun a11yEventLogDao(): A11yEventLog.A11yEventLogDao
}

@RenameColumn(
    tableName = "subs_config",
    fromColumnName = "subs_item_id",
    toColumnName = "subs_id"
)
@RenameColumn(
    tableName = "category_config",
    fromColumnName = "subs_item_id",
    toColumnName = "subs_id"
)
class Migration9To10Spec : AutoMigrationSpec

@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_name"
)
@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_version_code"
)
@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_version_name"
)
class Migration10To11Spec : AutoMigrationSpec

@Suppress("unused")
class DbConverters {
    @TypeConverter
    fun fromListStringToString(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

object DbSet {
    private val db by lazy {
        Room.databaseBuilder(
            app,
            AppDb::class.java,
            dbFolder.resolve("gkd.db").absolutePath
        ).fallbackToDestructiveMigration(false).build()
    }
    val subsItemDao get() = db.subsItemDao()
    val subsConfigDao get() = db.subsConfigDao()
    val snapshotDao get() = db.snapshotDao()
    val actionLogDao get() = db.actionLogDao()
    val categoryConfigDao get() = db.categoryConfigDao()
    val activityLogDao get() = db.activityLogDao()
    val appConfigDao get() = db.appConfigDao()
    val appVisitLogDao get() = db.appVisitLogDao()
    val a11yEventLogDao get() = db.a11yEventLogDao()
}
