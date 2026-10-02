package li.gkd.app.data

import android.view.accessibility.AccessibilityEvent
import li.gkd.db.A11yEventLog

fun AccessibilityEvent.toA11yEventLog(id: Int) = A11yEventLog(
    id = id,
    ctime = System.currentTimeMillis(),
    type = eventType,
    appId = packageName.toString(),
    name = className.toString(),
    desc = contentDescription?.toString(),
    text = text.map { it.toString() },
)
