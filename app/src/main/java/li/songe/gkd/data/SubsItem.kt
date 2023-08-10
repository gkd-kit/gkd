package li.songe.gkd.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.FolderExt
import java.io.File

@Entity(
    tableName = "subs_item",
)
@Parcelize
data class SubsItem(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") val mtime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "enable") val enable: Boolean = true,
    @ColumnInfo(name = "enable_update") val enableUpdate: Boolean = true,
    @ColumnInfo(name = "order") val order: Int = 1,

    //    订阅文件的根字段
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "author") val author: String = "",
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "update_url") val updateUrl: String = "",
    @ColumnInfo(name = "support_url") val supportUrl: String = "",

    ) : Parcelable {

    @IgnoredOnParcel
    val subsFile by lazy {
        File(FolderExt.subsFolder.absolutePath.plus("/${id}.json"))
    }

    @IgnoredOnParcel
    val subscriptionRaw by lazy {
        try {
            SubscriptionRaw.parse5(subsFile.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun removeAssets() {
        DbSet.subsItemDao.delete(this)
        withContext(IO) {
            subsFile.exists() && subsFile.delete()
        }
        DbSet.subsConfigDao.deleteSubs(id)
    }

    companion object {
        fun getSubscriptionRaw(subsItemId: Long): SubscriptionRaw? {
            return try {
                SubscriptionRaw.parse5(File(FolderExt.subsFolder.absolutePath.plus("/${subsItemId}.json")).readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }


    @Dao
    interface SubsItemDao {
        @Update
        suspend fun update(vararg objects: SubsItem): Int

        @Insert
        suspend fun insert(vararg users: SubsItem): List<Long>

        @Delete
        suspend fun delete(vararg users: SubsItem): Int

        @Query("SELECT * FROM subs_item ORDER BY `order`")
        fun query(): Flow<List<SubsItem>>

        @Query("SELECT * FROM subs_item WHERE id=:id")
        fun queryById(id: Long): SubsItem?
    }


}
