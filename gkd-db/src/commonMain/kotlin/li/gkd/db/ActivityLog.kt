package li.gkd.db

import androidx.paging.PagingSource
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.DeleteTable
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "activity_log",
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
) {
    @Dao
    @DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
    interface ActivityLogDao {
        @Insert
        suspend fun insert(vararg objects: ActivityLog): List<Long>

        @Query("SELECT * FROM activity_log ORDER BY ctime DESC ")
        fun pagingSource(): PagingSource<Int, ActivityLog>

        @Query("SELECT COUNT(*) FROM activity_log")
        fun count(): Flow<Int>

        @Query(
            """
            DELETE FROM activity_log
            WHERE (
                    SELECT COUNT(*)
                    FROM activity_log
                ) > 500
                AND ctime <= (
                    SELECT ctime
                    FROM activity_log
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
