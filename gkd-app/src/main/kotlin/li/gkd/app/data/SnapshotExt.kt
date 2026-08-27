package li.gkd.app.data

import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.util.format
import li.gkd.db.Snapshot

val Snapshot.screenshotFile
    get() = SnapshotStore.screenshotFile(id)

val Snapshot.date: String
    get() = id.format("MM-dd HH:mm:ss")
