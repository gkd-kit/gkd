package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(
    tableName = "snapshot",
)
@Serializable
data class Snapshot(
    @PrimaryKey @ColumnInfo(name = "id") override val id: Long,

    @ColumnInfo(name = "app_id") override val appId: String,
    @ColumnInfo(name = "activity_id") override val activityId: String?,

    @ColumnInfo(name = "screen_height") override val screenHeight: Int,
    @ColumnInfo(name = "screen_width") override val screenWidth: Int,
    @ColumnInfo(name = "is_landscape") override val isLandscape: Boolean,

    @ColumnInfo(name = "github_asset_id") val githubAssetId: Int? = null,

    ) : BaseSnapshot {
    @Dao
    interface SnapshotDao {
        @Update
        suspend fun update(vararg objects: Snapshot): Int

        @Insert
        suspend fun insert(vararg users: Snapshot): List<Long>

        @Query("DELETE FROM snapshot")
        suspend fun deleteAll()

        @Delete
        suspend fun delete(vararg users: Snapshot): Int

        @Query("SELECT * FROM snapshot ORDER BY id DESC")
        fun query(): Flow<List<Snapshot>>

        @Query("UPDATE snapshot SET github_asset_id=null WHERE id = :id")
        suspend fun deleteGithubAssetId(id: Long)

        @Query("SELECT COUNT(*) FROM snapshot")
        fun count(): Flow<Int>
    }
}
