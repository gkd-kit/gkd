package li.gkd.app.crash

import java.io.File

class CrashRestartGuard(private val file: File) {
    @Synchronized
    fun recordCrash(now: Long): Boolean {
        val recent = if (file.exists()) file.readLines().map { it.toLong() } else emptyList()
        val crashes = recent.filter { now - it in 0 until 60_000L } + now
        file.writeText(crashes.takeLast(3).joinToString("\n"))
        return crashes.size < 3
    }
}
