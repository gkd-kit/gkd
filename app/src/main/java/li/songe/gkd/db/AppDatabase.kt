package li.songe.gkd.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import li.songe.gkd.App

@Database(entities = [Rule::class, RuleGroup::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun ruleGroupDao(): RuleGroupDao

    companion object {
        val db by lazy {
            var basePath = (App.context.getExternalFilesDir(null)?.absolutePath ?: "")
            var name = "database.db"
            if (basePath.isNotEmpty()) {
                if (!basePath.endsWith("/")) {
                    basePath += "/"
                }
                name = basePath + name
            }
            Room.databaseBuilder(
                App.context,
                AppDatabase::class.java,
                name
            ).build()
        }
    }
}