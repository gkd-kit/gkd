package li.gkd.db

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

@Entity(
    tableName = "activity_log_v2",
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
) {
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
                ) > 500
                AND ctime <= (
                    SELECT ctime
                    FROM activity_log_v2
                    ORDER BY ctime DESC
                    LIMIT 1 OFFSET 500
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
