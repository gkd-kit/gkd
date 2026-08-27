package li.gkd.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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
