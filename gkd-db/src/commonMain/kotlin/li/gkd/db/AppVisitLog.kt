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
    tableName = "app_visit_log",
)
data class AppVisitLog(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "mtime") val mtime: Long,
) {
    @Dao
    interface AppLogDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg objects: AppVisitLog): List<Long>

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
