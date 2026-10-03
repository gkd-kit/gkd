package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "app_last_visit",
)
data class AppLastVisit(
    @PrimaryKey @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "last_visit_time") val lastVisitTime: Long,
) {
    @Dao
    interface AppLastVisitDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg objects: AppLastVisit): List<Long>

        @Query("SELECT DISTINCT app_id FROM app_last_visit ORDER BY last_visit_time DESC")
        fun query(): Flow<List<String>>

        @Query(
            """
            DELETE FROM app_last_visit
            WHERE last_visit_time <= (
                    SELECT last_visit_time
                    FROM app_last_visit
                    ORDER BY last_visit_time DESC
                    LIMIT 1 OFFSET 500
                )
        """
        )
        suspend fun deleteKeepLatest(): Int
    }
}
