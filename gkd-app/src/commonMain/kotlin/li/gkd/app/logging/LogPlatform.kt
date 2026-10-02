package li.gkd.app.logging

internal expect fun formatLogValue(value: Any?): String
internal expect fun writePlatformLog(tag: String, message: String)
internal expect fun logMetadata(): LogMetadata
internal expect fun isLogDebuggable(): Boolean
