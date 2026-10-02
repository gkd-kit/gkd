package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "subs_category_config",
    primaryKeys = ["subs_id", "category_key"],
    foreignKeys = [ForeignKey(
        entity = SubsItem::class,
        parentColumns = ["id"],
        childColumns = ["subs_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SubsCategoryConfig(
    @ColumnInfo(name = "enable") val enable: Boolean? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "category_key") val categoryKey: Int,
) {
    @Dao
    interface SubsCategoryConfigDao {

        @Query("SELECT * FROM subs_category_config")
        suspend fun queryAll(): List<SubsCategoryConfig>

        @Update
        suspend fun update(vararg objects: SubsCategoryConfig): Int

        @Upsert
        suspend fun upsert(vararg objects: SubsCategoryConfig)

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg objects: SubsCategoryConfig): List<Long>

        @Delete
        suspend fun delete(vararg objects: SubsCategoryConfig): Int

        @Query("DELETE FROM subs_category_config WHERE subs_id IN (:subsIds)")
        suspend fun deleteBySubsId(vararg subsIds: Long): Int

        @Query("DELETE FROM subs_category_config WHERE subs_id=:subsItemId AND category_key=:categoryKey")
        suspend fun deleteByCategoryKey(subsItemId: Long, categoryKey: Int): Int

        @Query("SELECT * FROM subs_category_config WHERE subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedList(): Flow<List<SubsCategoryConfig>>

        @Query("SELECT * FROM subs_category_config WHERE subs_id=:subsItemId")
        fun queryConfig(subsItemId: Long): Flow<List<SubsCategoryConfig>>

        @Query("SELECT * FROM subs_category_config WHERE subs_id IN (:subsItemIds)")
        suspend fun querySubsItemConfig(subsItemIds: List<Long>): List<SubsCategoryConfig>

        @Query("SELECT * FROM subs_category_config WHERE subs_id IN (:subsItemIds)")
        fun queryBySubsIds(subsItemIds: List<Long>): Flow<List<SubsCategoryConfig>>

    }
}
