package li.gkd.app.model

import li.gkd.app.time.format
import li.gkd.db.Snapshot

val Snapshot.date: String get() = id.format("MM-dd HH:mm:ss")
