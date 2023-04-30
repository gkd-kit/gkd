package li.songe.gkd.db.util

import androidx.sqlite.db.SimpleSQLiteQuery
import li.songe.gkd.db.AppDatabase.Companion.db
import li.songe.gkd.db.BaseDao
import li.songe.gkd.db.BaseTable
import li.songe.gkd.db.table.*
import kotlin.reflect.KClass


object RoomX {
    // 把表类和具体数据库方法关联起来
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getBaseDao(cls: KClass<T>) = when (cls) {
        SubsItem::class -> db.subsItemRoomDao()
//        SubsAppItem::class -> db.subsAppItemRoomDao()
//        SubsGroupItem::class -> db.subsGroupItemRoomDao()
//        SubsRuleItem::class -> db.subsRuleItemRoomDao()
        SubsConfig::class -> db.subsConfigRoomDao()
        else -> throw Exception("not found class dao : ${cls::class.java.name}")
    } as BaseDao<T>

    fun databaseBeforeHook(vararg objects: Any) {
        objects.forEach { /**/ when (it) {
            is BaseTable -> {
                it.mtime = System.currentTimeMillis()
            }
            else -> throw Exception("not found table class hook : ${it::class.java.name}")
        }
        }
    }


    fun databaseInsertAfterHook(objects: Array<out Any>, idList: List<Long>) {
        objects.forEachIndexed { index, any ->  /**/ when (any) {
            is BaseTable -> {
//                插入数据后更新实体类的id
                any.id = idList[index]
            }
            else -> throw Exception("not found table class hook : ${any::class.java.name}")
        }
        }
    }

    suspend inline fun <reified T : Any> update(vararg objects: T): Int {
        databaseBeforeHook(*objects)
        return getBaseDao(T::class).update(*objects)
    }

    /**
     * 插入成功后, 自动改变入参对象的 id
     */
    suspend inline fun <reified T : Any> insert(vararg objects: T): List<Long> {
        databaseBeforeHook(*objects)
        return getBaseDao(T::class).insert(*objects).apply {
            databaseInsertAfterHook(objects, this)
        }
    }

    suspend inline fun <reified T : Any> delete(vararg objects: T) =
        getBaseDao(T::class).delete(*objects)

    suspend inline fun <reified T : Any> select(
        limit: Int? = null,
        offset: Int? = null,
        noinline block: (() -> Expression<*, *, T>)? = null
    ): List<T> {
        val expression = block?.invoke()
        val tableName = RoomAnnotation.getTableName(T::class.java.name)
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
        val tableName = RoomAnnotation.getTableName(T::class.java.name)
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

//    inline fun <reified T : Any> selectFlow(
//        limit: Int? = null,
//        offset: Int? = null,
//        noinline block: (() -> Expression<*, *, T>)? = null
//    ): Flow<List<T>> {
//        val expression = block?.invoke()
//        val tableName = RoomAnnotation.getTableName(T::class.java.name)
//        val sqlString = "SELECT * FROM $tableName" + (if (expression != null) {
//            " WHERE ${expression.stringify()}"
//        } else {
//            ""
//        }) + (if (limit != null) {
//            " LIMIT $limit"
//        } else {
//            ""
//        }) + (if (offset != null) {
//            " OFFSET $offset"
//        } else {
//            ""
//        })
//        val baseDao = getBaseDao(T::class)
//        return baseDao.queryFlow(SimpleSQLiteQuery(sqlString))
//    }


//    fun testExample() = runBlocking {
//        select { SubsItem::filePath like likeString().any(".json") }.forEach {
//            LogUtils.d(it)
//        }
//
//        selectFlow { SubsItem::description like likeString().any(".json") }.distinctUntilChanged()
//            .collect {
//                LogUtils.d(it.firstOrNull())
//            }
//    }

}

