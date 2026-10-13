package li.gkd.app.logging

import li.gkd.app.META

actual fun logMetadata() = LogMetadata(
    systemName = "Desktop",
    systemVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
    device = "${System.getProperty("os.arch")} JVM ${System.getProperty("java.version")}",
    app = "GKD Desktop ${META.versionName} (${META.versionCode})",
)

actual fun formatLogValue(value: Any?): String =
    if (value is Throwable) value.stackTraceToString() else value.toString()

actual fun writePlatformLog(tag: String, message: String) {
    System.err.println("$tag: $message")
}
