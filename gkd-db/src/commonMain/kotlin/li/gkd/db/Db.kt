package li.gkd.db

import androidx.room3.withWriteTransaction

object Db {
    private var createDatabase: (() -> AppDb)? = null

    internal fun initialize(createDatabase: () -> AppDb) {
        check(this.createDatabase == null) { "Db is already initialized" }
        this.createDatabase = createDatabase
    }

    private val database by lazy {
        checkNotNull(createDatabase) { "Db is not initialized" }.invoke()
    }

    val subsItemDao get() = database.subsItemDao()
    val subsConfigDao get() = database.subsConfigDao()
    val snapshotDao get() = database.snapshotDao()
    val actionLogDao get() = database.actionLogDao()
    val categoryConfigDao get() = database.categoryConfigDao()
    val activityLogDao get() = database.activityLogDao()
    val appConfigDao get() = database.appConfigDao()
    val appVisitLogDao get() = database.appVisitLogDao()
    val a11yEventLogDao get() = database.a11yEventLogDao()

    suspend fun <T> withTransaction(block: suspend () -> T): T =
        database.withWriteTransaction { block() }
}
