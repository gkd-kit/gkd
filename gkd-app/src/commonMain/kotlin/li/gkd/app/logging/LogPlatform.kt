package li.gkd.app.logging

expect fun formatLogValue(value: Any?): String
expect fun writePlatformLog(tag: String, message: String)
expect fun logMetadata(): LogMetadata
expect fun isLogDebuggable(): Boolean
