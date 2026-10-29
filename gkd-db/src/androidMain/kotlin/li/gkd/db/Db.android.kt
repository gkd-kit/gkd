package li.gkd.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun Db.initialize(context: Context, databasePath: String) {
    val applicationContext = context.applicationContext
    initialize {
        Room.databaseBuilder(
                applicationContext,
                AppDb::class.java,
                databasePath,
            )
            .addMigrations(Migration14To15)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
