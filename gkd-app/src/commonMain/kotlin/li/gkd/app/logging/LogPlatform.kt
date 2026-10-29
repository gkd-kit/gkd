package li.gkd.app.logging

expect fun formatLogValue(value: Any?): String

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

expect fun writePlatformLog(level: LogLevel, tag: String, message: String)

expect fun logMetadata(): LogMetadata
