package li.songe.gkd.db

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

interface BaseDao<T : Any> {
    @Insert
    suspend fun insert(vararg objects: T): List<Long>

    @Delete
    suspend fun delete(vararg objects: T): Int

    @Update
    suspend fun update(vararg objects: T): Int

    @RawQuery
    suspend fun query(sqLiteQuery: SupportSQLiteQuery): List<T>

    @RawQuery
    suspend fun delete(sqLiteQuery: SupportSQLiteQuery): List<Int>

    // https://developer.android.com/training/data-storage/room/async-queries#kotlin
//    you must set observedEntities in sub interface
//    @RawQuery
//    fun queryFlow(sqLiteQuery: SupportSQLiteQuery): Flow<List<T>>
}