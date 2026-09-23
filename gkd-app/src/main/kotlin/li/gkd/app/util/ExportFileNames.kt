package li.gkd.app.util

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ExportFileNames {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)

    private fun name(stem: String, extension: String, suffix: Int): String =
        "$stem${if (suffix == 1) "" else "-$suffix"}${if (extension.isEmpty()) "" else ".$extension"}"

    fun timestamp(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        timestampFormatter.format(Instant.ofEpochMilli(timeMillis).atZone(zoneId))

    fun availableName(stem: String, extension: String, isTaken: (String) -> Boolean): String {
        var suffix = 1
        while (true) {
            val candidate = name(stem, extension, suffix)
            if (!isTaken(candidate)) return candidate
            suffix++
        }
    }

    fun reserve(directory: File, stem: String, extension: String): File {
        var suffix = 1
        while (true) {
            val file = directory.resolve(name(stem, extension, suffix))
            if (file.createNewFile()) return file
            suffix++
        }
    }
}
