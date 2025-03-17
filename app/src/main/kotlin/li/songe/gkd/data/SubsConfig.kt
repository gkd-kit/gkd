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
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable


private var lastId = 0L

@Synchronized
private fun buildUniqueTimeMillisId(): Long {
    while (true) {
        val id = System.currentTimeMillis()
        if (id != lastId) {
            lastId = id
            return id
        }
        Thread.sleep(1)
    }
}

@Serializable
@Entity(
    tableName = "subs_config",
)
@Parcelize
data class SubsConfig(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = buildUniqueTimeMillisId(),
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "enable") val enable: Boolean? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String = "",
    @ColumnInfo(name = "group_key") val groupKey: Int = -1,
    @ColumnInfo(name = "exclude", defaultValue = "") val exclude: String = "",
) : Parcelable {

    @Suppress("ConstPropertyName")
    companion object {
        const val AppGroupType = 2
        const val GlobalGroupType = 3
    }

    @Dao
    interface SubsConfigDao {

        @Update
        suspend fun update(vararg objects: SubsConfig): Int

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg users: SubsConfig): List<Long>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg users: SubsConfig): List<Long>

        @Delete
        suspend fun delete(vararg users: SubsConfig): Int

        @Transaction
        suspend fun insertAndDelete(newList: List<SubsConfig>, deleteList: List<SubsConfig>) {
            insert(*newList.toTypedArray())
            delete(*deleteList.toTypedArray())
        }

        @Query("DELETE FROM subs_config WHERE subs_id=:subsItemId")
        suspend fun delete(subsItemId: Long): Int

        @Query("DELETE FROM subs_config WHERE subs_id IN (:subsIds)")
        suspend fun deleteBySubsId(vararg subsIds: Long): Int

        @Query("DELETE FROM subs_config WHERE subs_id=:subsItemId AND app_id=:appId")
        suspend fun deleteAppConfig(subsItemId: Long, appId: String): Int

        @Query("DELETE FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key=:groupKey")
        suspend fun deleteAppGroupConfig(subsItemId: Long, appId: String, groupKey: Int): Int


        @Query("DELETE FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key IN (:keyList)")
        suspend fun batchDeleteAppGroupConfig(
            subsItemId: Long,
            appId: String,
            keyList: List<Int>
        ): Int

        @Query("DELETE FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId AND group_key=:groupKey")
        suspend fun deleteGlobalGroupConfig(subsItemId: Long, groupKey: Int): Int

        @Query("DELETE FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId AND group_key IN (:keyList)")
        suspend fun batchDeleteGlobalGroupConfig(subsItemId: Long, keyList: List<Int>): Int

        @Query("SELECT * FROM subs_config WHERE subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedList(): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId")
        fun querySubsGroupTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId")
        fun queryAppGroupTypeConfig(subsItemId: Long, appId: String): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key=:groupKey")
        fun queryAppGroupTypeConfig(
            subsItemId: Long, appId: String, groupKey: Int
        ): Flow<SubsConfig?>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId")
        fun queryGlobalGroupTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId AND group_key=:groupKey")
        fun queryGlobalGroupTypeConfig(subsItemId: Long, groupKey: Int): Flow<SubsConfig?>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND app_id=:appId AND subs_id IN (:subsItemIds)")
        fun queryAppConfig(subsItemIds: List<Long>, appId: String): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id IN (:subsItemIds)")
        fun queryGlobalConfig(subsItemIds: List<Long>): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedGlobalConfig(): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE subs_id IN (:subsItemIds) ")
        suspend fun querySubsItemConfig(subsItemIds: List<Long>): List<SubsConfig>

        @Query("UPDATE subs_config SET enable = null WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key=:groupKey AND enable IS NOT NULL")
        suspend fun resetAppGroupTypeEnable(subsItemId: Long, appId: String, groupKey: Int): Int

        @Transaction
        suspend fun batchResetAppGroupEnable(
            subsItemId: Long,
            list: List<Pair<RawSubscription.RawAppGroup, RawSubscription.RawApp>>
        ): List<Pair<RawSubscription.RawAppGroup, RawSubscription.RawApp>> {
            return list.filter { (g, a) ->
                resetAppGroupTypeEnable(subsItemId, a.id, g.key) > 0
            }
        }
    }

}

data class ExcludeData(
    val appIds: Map<String, Boolean>,
    val activityIds: Set<Pair<String, String>>,
) {
    val excludeAppIds = appIds.entries.filter { e -> e.value }.map { e -> e.key }.toHashSet()
    val includeAppIds = appIds.entries.filter { e -> !e.value }.map { e -> e.key }.toHashSet()

    fun stringify(appId: String? = null): String {
        return if (appId != null) {
            activityIds.filter { e -> e.first == appId }.map { e -> e.second }.sorted()
                .joinToString("\n")
        } else {
            (appIds.entries.map { e ->
                if (e.value) {
                    e.key
                } else {
                    "!${e.key}"
                }
            } + activityIds.map { e -> "${e.first}/${e.second}" }).sorted().joinToString("\n")
        }
    }

    fun clear(appId: String): ExcludeData {
        return copy(
            appIds = appIds.toMutableMap().apply {
                remove(appId)
            },
        )
    }

    fun switch(appId: String, activityId: String? = null): ExcludeData {
        return if (activityId == null) {
            copy(
                appIds = appIds.toMutableMap().apply {
                    if (get(appId) != false) {
                        set(appId, false)
                    } else {
                        set(appId, true)
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

    companion object {
        private val empty = ExcludeData(emptyMap(), emptySet())
        fun parse(exclude: String?): ExcludeData {
            if (exclude == null || exclude.isBlank()) {
                return empty
            }
            val appIds = mutableMapOf<String, Boolean>()
            val activityIds = mutableSetOf<Pair<String, String>>()
            exclude.split('\n', ',').filter { s -> s.isNotBlank() }.map { s -> s.trim() }
                .forEach { s ->
                    if (s[0] == '!') {
                        appIds[s.substring(1)] = false
                    } else {
                        val a = s.split('/')
                        val appId = a[0]
                        val activityId = a.getOrNull(1)
                        if (activityId != null) {
                            activityIds.add(appId to activityId)
                        } else {
                            appIds[appId] = true
                        }
                    }
                }
            return ExcludeData(
                appIds = appIds,
                activityIds = activityIds,
            )
        }

        fun parse(appId: String, exclude: String?): ExcludeData {
            return parse((exclude ?: "").split('\n', ',').joinToString("\n") { "$appId/$it" })
        }
    }
}
