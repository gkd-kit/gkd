package li.gkd.app.model

import kotlinx.serialization.SerializationException
import li.gkd.app.util.JsonUtils
import li.gkd.db.A11yEventLog

fun A11yEventLog.toLogItem(): A11yEventLogItem =
    A11yEventLogItem(
        eventLog = this,
        parsedText =
            try {
                Result.success(
                    if (text.isEmpty()) emptyList()
                    else JsonUtils.default.decodeFromString<List<String>>(text),
                )
            } catch (error: SerializationException) {
                Result.failure(error)
            },
    )

val A11yEventLog.isStateChanged: Boolean
    get() = type == 0x20 // Android event type persisted in the log database.

val A11yEventLog.fixedName: String
    get() {
        if (isStateChanged && name.startsWith("$appId.")) {
            return name.substring(appId.length)
        }
        if (
            name.contains("View") || name.contains("Layout") || viewSuffixes.any(name::startsWith)
        ) {
            return name.substring(name.lastIndexOf('.') + 1)
        }
        return name
    }

private val viewSuffixes =
    listOf(
        "android.widget.",
        "android.view.",
        "android.support.",
    )
