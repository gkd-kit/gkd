package li.songe.gkd.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.META
import li.songe.gkd.a11y.launcherAppId
import li.songe.gkd.util.systemUiAppId

@Entity(
    tableName = "app_visit_log",
)
data class AppVisitLog(
    @PrimaryKey() @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "mtime") val mtime: Long,
) {
    @Dao
    interface AppLogDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg objects: AppVisitLog): List<Long>

        @Transaction
        suspend fun insert(oldAppId: String, newAppId: String, mtime: Long) {
            insert(
                AppVisitLog(oldAppId, fixAppVisitTime(oldAppId, mtime - 1)),
                AppVisitLog(newAppId, fixAppVisitTime(newAppId, mtime)),
            )
            if (appLogCount++ % 100 == 0) {
                deleteKeepLatest()
            }
        }

        @Query("SELECT DISTINCT id FROM app_visit_log ORDER BY mtime DESC")
        fun query(): Flow<List<String>>

        @Query(
            """
            DELETE FROM app_visit_log
            WHERE (
                    SELECT COUNT(*)
                    FROM app_visit_log
                ) > 500
                AND mtime <= (
                    SELECT mtime
                    FROM app_visit_log
                    ORDER BY mtime DESC
                    LIMIT 1 OFFSET 500
                )
        """
        )
        suspend fun deleteKeepLatest(): Int
    }
}

private fun fixAppVisitTime(appId: String, t: Long): Long = when (appId) {
    META.appId, launcherAppId, systemUiAppId -> t - 60_000
    else -> t
}

private var appLogCount = 0
