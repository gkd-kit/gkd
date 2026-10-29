package li.gkd.app.data

import android.view.accessibility.AccessibilityEvent
import li.gkd.app.model.A11yEventLogItem
import li.gkd.app.util.JsonUtils
import li.gkd.db.A11yEventLog

fun AccessibilityEvent.toA11yEventLogItem(id: Int): A11yEventLogItem {
    val text = text.map { it.toString() }
    return A11yEventLogItem(
        eventLog =
            A11yEventLog(
                id = id,
                ctime = System.currentTimeMillis(),
                type = eventType,
                appId = packageName.toString(),
                name = className.toString(),
                desc = contentDescription?.toString(),
                text = JsonUtils.default.encodeToString(text),
            ),
        parsedText = Result.success(text),
    )
}
