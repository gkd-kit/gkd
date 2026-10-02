package li.gkd.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun Db.initialize(databasePath: String) {
    initialize {
        Room.databaseBuilder<AppDb>(databasePath, factory = AppDbConstructor::initialize)
            .addMigrations(Migration14To15)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
