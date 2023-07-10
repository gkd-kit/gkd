package li.songe.gkd.db

import androidx.room.Database
import androidx.room.RoomDatabase
import li.songe.gkd.data.Snapshot

@Database(
    version = 1,
    entities = [Snapshot::class],
)
abstract class SnapshotDb: RoomDatabase() {
    abstract fun snapshotDao(): Snapshot.SnapshotDao
}