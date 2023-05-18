package li.songe.gkd.db.util

import androidx.sqlite.db.SimpleSQLiteQuery
import li.songe.gkd.db.AppDatabase.Companion.db
import li.songe.gkd.db.BaseDao
import li.songe.gkd.db.LogDatabase.Companion.logDb
import li.songe.gkd.db.table.*
import kotlin.reflect.KClass


object RoomX {
    // 把表类和具体数据库方法关联起来
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getBaseDao(cls: KClass<T>) = when (cls) {
        SubsItem::class -> db.subsItemRoomDao()
        SubsConfig::class -> db.subsConfigRoomDao()
        TriggerLog::class -> logDb.triggerLogRoomDao()
        else -> error("not found class dao : ${cls::class.java.name}")
    } as BaseDao<T>


    suspend inline fun <reified T : Any> update(vararg objects: T): Int {
        return getBaseDao(T::class).update(*objects)
    }

    /**
     * 插入成功后, 自动改变入参对象的 id
     */
    suspend inline fun <reified T : Any> insert(vararg objects: T): List<Long> {
        return getBaseDao(T::class).insert(*objects)
    }

    suspend inline fun <reified T : Any> delete(vararg objects: T) =
        getBaseDao(T::class).delete(*objects)

    suspend inline fun <reified T : Any> select(
        limit: Int? = null,
        offset: Int? = null,
        noinline block: (() -> Expression<*, *, T>)? = null
    ): List<T> {
        val expression = block?.invoke()
        val tableName = RoomAnnotation.getTableName(T::class)
        val sqlString = "SELECT * FROM $tableName" + (if (expression != null) {
            " WHERE ${expression.stringify()}"
        } else {
            ""
        }) + (if (limit != null) {
            " LIMIT $limit"
        } else {
            ""
        }) + (if (offset != null) {
            " OFFSET $offset"
        } else {
            ""
        })
        val baseDao = getBaseDao(T::class)
        return baseDao.query(SimpleSQLiteQuery(sqlString))
    }

    suspend inline fun <reified T : Any> delete(
        limit: Int? = null,
        offset: Int? = null,
        noinline block: (() -> Expression<*, *, T>)? = null
    ): List<Int> {
        val expression = block?.invoke()
        val tableName = RoomAnnotation.getTableName(T::class)
        val sqlString = "DELETE FROM $tableName" + (if (expression != null) {
            " WHERE ${expression.stringify()}"
        } else {
            ""
        }) + (if (limit != null) {
            " LIMIT $limit"
        } else {
            ""
        }) + (if (offset != null) {
            " OFFSET $offset"
        } else {
            ""
        })
        val baseDao = getBaseDao(T::class)
        return baseDao.delete(SimpleSQLiteQuery(sqlString))
    }
}

