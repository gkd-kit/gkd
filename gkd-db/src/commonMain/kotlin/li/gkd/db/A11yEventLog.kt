package li.gkd.db

import androidx.paging.PagingSource
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "a11y_event_log")
class A11yEventLog(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "ctime") val ctime: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "appId") val appId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "desc") val desc: String?,
    @ColumnInfo(name = "text") val text: List<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is A11yEventLog) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }

    @Dao
    @DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
    interface A11yEventLogDao {
        @Insert
        suspend fun insert(objects: List<A11yEventLog>): List<Long>

        @Query("DELETE FROM a11y_event_log")
        suspend fun deleteAll()

        @Query("SELECT COUNT(*) FROM a11y_event_log")
        fun count(): Flow<Int>

        @Query("SELECT * FROM a11y_event_log ORDER BY ctime DESC ")
        fun pagingSource(): PagingSource<Int, A11yEventLog>

        @Query("SELECT MAX(id) FROM a11y_event_log")
        suspend fun maxId(): Int?

        @Query(
            """
            DELETE FROM a11y_event_log
            WHERE (
                    SELECT COUNT(*)
                    FROM a11y_event_log
                ) > 1000
                AND id <= (
                    SELECT id
                    FROM a11y_event_log
                    ORDER BY id DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int


    }

}
