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
    @ColumnInfo(name = "enable") val enable: Boolean? = null,
    @ColumnInfo(name = "subs_item_id") val subsItemId: Long,
    @ColumnInfo(name = "app_id") val appId: String = "",
    @ColumnInfo(name = "group_key") val groupKey: Int = -1,
    @ColumnInfo(name = "exclude", defaultValue = "") val exclude: String = "",
) : Parcelable {

    companion object {
        const val AppType = 1
        const val AppGroupType = 2
        const val GlobalGroupType = 3
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
        suspend fun delete(subsItemId: Long): Int

        @Query("DELETE FROM subs_config WHERE subs_item_id=:subsItemId AND app_id=:appId")
        suspend fun delete(subsItemId: Long, appId: String): Int

        @Query("DELETE FROM subs_config WHERE subs_item_id=:subsItemId AND app_id=:appId AND group_key=:groupKey")
        suspend fun delete(subsItemId: Long, appId: String, groupKey: Int): Int

        @Query("SELECT * FROM subs_config")
        fun query(): Flow<List<SubsConfig>>


        @Query("SELECT * FROM subs_config WHERE type=${AppType} AND subs_item_id=:subsItemId")
        fun queryAppTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_item_id=:subsItemId")
        fun querySubsGroupTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_item_id=:subsItemId AND app_id=:appId")
        fun queryAppGroupTypeConfig(subsItemId: Long, appId: String): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_item_id=:subsItemId AND app_id=:appId AND group_key=:groupKey")
        fun queryAppGroupTypeConfig(
            subsItemId: Long, appId: String, groupKey: Int
        ): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_item_id=:subsItemId")
        fun queryGlobalGroupTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_item_id=:subsItemId AND group_key=:groupKey")
        fun queryGlobalGroupTypeConfig(subsItemId: Long, groupKey: Int): Flow<List<SubsConfig>>
    }

}

data class ExcludeData(
    val appIds: Set<String>, val activityIds: Set<Pair<String, String>>
) {
    companion object {
        fun parse(exclude: String?): ExcludeData {
            val appIds = mutableSetOf<String>()
            val activityIds = mutableSetOf<Pair<String, String>>()
            (exclude ?: "").split('\n', ',')
                .filter { s -> s.isNotBlank() && s.count { c -> c == '/' } <= 1 }.forEach { s ->
                    val a = s.split('/')
                    val appId = a[0]
                    val activityId = a.getOrNull(1)
                    if (activityId != null) {
                        activityIds.add(appId to activityId)
                    } else {
                        appIds.add(appId)
                    }
                }
            return ExcludeData(appIds = appIds, activityIds = activityIds)
        }

        fun parse(appId: String, exclude: String?): ExcludeData {
            return parse((exclude ?: "").split('\n', ',').joinToString("\n") { "$appId/$it" })
        }
    }
}

fun ExcludeData.stringify(): String {
    return (appIds + activityIds.map { e -> "${e.first}/${e.second}" }).sorted().joinToString("\n")
}

fun ExcludeData.stringify(appId: String): String {
    return activityIds.filter { e -> e.first == appId }.map { e -> e.second }.sorted()
        .joinToString("\n")
}

fun ExcludeData.switch(appId: String, activityId: String? = null): ExcludeData {
    return if (activityId == null) {
        copy(
            appIds = appIds.toMutableSet().apply {
                if (contains(appId)) {
                    remove(appId)
                } else {
                    add(appId)
                }
            },
        )
    } else {
        copy(activityIds = activityIds.toMutableSet().apply {
            val e = appId to activityId
            if (contains(e)) {
                remove(e)
            } else {
                add(e)
            }
        })
    }
}
