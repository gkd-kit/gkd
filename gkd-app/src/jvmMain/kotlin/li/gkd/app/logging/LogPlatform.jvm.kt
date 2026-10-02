package li.gkd.app.logging

import li.gkd.app.DesktopProfile

actual fun logMetadata() = LogMetadata(
    systemName = "Desktop",
    systemVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
    device = "${System.getProperty("os.arch")} JVM ${System.getProperty("java.version")}",
    app = "GKD Desktop ${DesktopProfile.versionName} (${DesktopProfile.versionCode})",
)

actual fun isLogDebuggable(): Boolean = DesktopProfile.debuggable

actual fun formatLogValue(value: Any?): String =
    if (value is Throwable) value.stackTraceToString() else value.toString()

actual fun writePlatformLog(tag: String, message: String) {
    System.err.println("$tag: $message")
}
