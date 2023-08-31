package li.songe.gkd.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "subs_config",
)
@Parcelize
data class SubsConfig(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "enable") val enable: Boolean,

    @ColumnInfo(name = "subs_item_id") val subsItemId: Long,
    @ColumnInfo(name = "app_id") val appId: String = "",
    @ColumnInfo(name = "group_key") val groupKey: Int = -1,
) : Parcelable {

    companion object {
        const val AppType = 1
        const val GroupType = 2
    }

    @Dao
    interface SubsConfigDao {

        @Update
        suspend fun update(vararg objects: SubsConfig): Int

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg users: SubsConfig): List<Long>

        @Delete
        suspend fun delete(vararg users: SubsConfig): Int

        @Query("DELETE FROM subs_config WHERE subs_item_id=:subsItemId")
        suspend fun deleteSubs(subsItemId: Long): Int

        @Query("SELECT * FROM subs_config")
        fun query(): Flow<List<SubsConfig>>


        @Query("SELECT * FROM subs_config WHERE type=${AppType} and subs_item_id=:subsItemId")
        fun queryAppTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GroupType} and subs_item_id=:subsItemId and app_id=:appId")
        fun queryGroupTypeConfig(subsItemId: Long, appId: String): Flow<List<SubsConfig>>
    }

}
