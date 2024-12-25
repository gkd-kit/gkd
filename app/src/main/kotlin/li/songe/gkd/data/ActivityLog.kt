package li.songe.gkd.data

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.util.format
import li.songe.gkd.util.getShowActivityId

@Entity(
    tableName = "activity_log_v2",
)
data class ActivityLog(
    // 不使用时间戳作为主键的原因
    // https://github.com/gkd-kit/gkd/issues/704
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
) {
    val showActivityId by lazy { getShowActivityId(appId, activityId) }
    val date by lazy { ctime.format("MM-dd HH:mm:ss SSS") }

    @Dao
    interface ActivityLogDao {
        @Insert
        suspend fun insert(vararg objects: ActivityLog): List<Long>

        @Query("DELETE FROM activity_log_v2")
        suspend fun deleteAll()

        @Query("SELECT * FROM activity_log_v2 ORDER BY ctime DESC ")
        fun pagingSource(): PagingSource<Int, ActivityLog>

        @Query("SELECT COUNT(*) FROM activity_log_v2")
        fun count(): Flow<Int>

        @Query(
            """
            DELETE FROM activity_log_v2
            WHERE (
                    SELECT COUNT(*)
                    FROM activity_log_v2
                ) > 1000
                AND ctime <= (
                    SELECT ctime
                    FROM activity_log_v2
                    ORDER BY ctime DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int
    }


    @DeleteTable.Entries(
        DeleteTable(tableName = "activity_log")
    )
    class ActivityLogV2Spec : AutoMigrationSpec
}
