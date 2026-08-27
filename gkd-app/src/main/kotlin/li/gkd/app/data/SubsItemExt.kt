package li.gkd.app.data

import li.gkd.app.util.format
import li.gkd.db.SubsItem

val SubsItem.mtimeStr: String
    get() = mtime.format("yyyy-MM-dd HH:mm:ss")
